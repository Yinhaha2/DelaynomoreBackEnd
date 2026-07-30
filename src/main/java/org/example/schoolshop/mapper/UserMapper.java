package org.example.schoolshop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.example.schoolshop.domain.User;

public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE user SET wallet_frozen = IFNULL(wallet_frozen,0) + #{amount} " +
            "WHERE id = #{userId} AND wallet_balance - IFNULL(wallet_frozen,0) >= #{amount}")
    int freezePoints(@Param("userId") Long userId, @Param("amount") int amount);

    @Update("UPDATE user SET wallet_frozen = IFNULL(wallet_frozen,0) - #{amount} " +
            "WHERE id = #{userId} AND IFNULL(wallet_frozen,0) >= #{amount}")
    int unfreezePoints(@Param("userId") Long userId, @Param("amount") int amount);

    @Update("UPDATE user SET wallet_balance = wallet_balance - #{amount}, " +
            "wallet_frozen = IFNULL(wallet_frozen,0) - #{amount} " +
            "WHERE id = #{userId} AND IFNULL(wallet_frozen,0) >= #{amount}")
    int settleFrozen(@Param("userId") Long userId, @Param("amount") int amount);

    @Update("UPDATE user SET wallet_balance = wallet_balance + #{amount} WHERE id = #{userId}")
    int addPoints(@Param("userId") Long userId, @Param("amount") int amount);

    @Update("UPDATE user SET wallet_balance = wallet_balance - #{amount} " +
            "WHERE id = #{userId} AND wallet_balance - IFNULL(wallet_frozen,0) >= #{amount}")
    int deductPoints(@Param("userId") Long userId, @Param("amount") int amount);

    @Update("UPDATE user SET exp = IFNULL(exp,0) + #{exp}, level = #{level} WHERE id = #{userId}")
    int addExp(@Param("userId") Long userId, @Param("exp") int exp, @Param("level") int level);
}
