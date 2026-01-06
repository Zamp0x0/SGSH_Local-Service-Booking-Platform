package com.yjshz.consultant.service;

import com.yjshz.consultant.mapper.ReservationMapper;
import com.yjshz.consultant.mapper.ShopMapper;
import com.yjshz.consultant.pojo.Reservation;
import com.yjshz.consultant.pojo.Shop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReservationService {
    @Autowired
    private ReservationMapper reservationMapper;

    //1.添加预约信息的方法
    public void insert(Reservation reservation) {
        reservationMapper.insert(reservation);
    }

    //2.查询预约信息的方法(根据手机号查询)
    public List<Reservation> findByPhone(String phone) {
        return reservationMapper.findByPhone(phone);
    }
}
