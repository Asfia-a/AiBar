package com.tavern.mapper;

import com.tavern.entity.RechargeOrder;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RechargeMapper {

    @Insert("""
            INSERT INTO t_recharge_order(order_no, user_id, amount, token_count, pay_type, status, paid_at)
            VALUES(#{orderNo}, #{userId}, #{amount}, #{tokens}, #{payType}, #{status}, NOW())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RechargeOrder order);

    @Select("SELECT id, order_no, user_id, amount, token_count AS tokens, pay_type, status, created_at, paid_at FROM t_recharge_order WHERE user_id=#{userId} ORDER BY id DESC")
    List<RechargeOrder> listByUser(Long userId);
}
