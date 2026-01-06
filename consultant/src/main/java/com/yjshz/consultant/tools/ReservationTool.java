package com.yjshz.consultant.tools;

import com.yjshz.consultant.pojo.Reservation;
import com.yjshz.consultant.service.ReservationService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Component
public class ReservationTool {

    @Autowired
    private ReservationService reservationService;

    private static final DateTimeFormatter[] FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

    private LocalDateTime parseDateTime(String s) {
        if (s == null) return null;
        String input = s.trim();
        for (DateTimeFormatter f : FORMATTERS) {
            try {
                return LocalDateTime.parse(input, f);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    @Tool("预约到店消费服务")
    public String addReservation(
            @P("用户姓名") String name,
            @P("用户手机号") String phone,
            @P("预约到店消费时间,格式为: yyyy-MM-dd HH:mm 或 yyyy-MM-dd'T'HH:mm") String communicationTime,
            @P("预约指定的商家") String shopName
    ) {
        log.warn("ReservationTool.addReservation called: name={}, phone={}, time={}, shop={}",
                name, phone, communicationTime, shopName);

        LocalDateTime time = parseDateTime(communicationTime);
        if (time == null) {
            return "预约失败：时间格式不对，请用 2026-01-01 18:30 或 2026-01-01T18:30";
        }

        try {
            Reservation reservation = new Reservation(null, name, phone, time, shopName);
            reservationService.insert(reservation);
            return "预约成功✅";
        } catch (Exception e) {
            log.error("预约写入数据库失败", e);
            return "预约失败：数据库写入异常（请检查后端日志）";
        }
    }

    @Tool("根据用户手机号查询预约单")
    public List<Reservation> findReservation(@P("用户手机号") String phone) {
        return reservationService.findByPhone(phone);
    }
}
