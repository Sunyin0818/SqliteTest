package com.zw.sqliteTest.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@TableName("student") // 指定表名
public class Student {
    @TableId
    private Integer id;

    @TableField("name")
    private String name;

    @TableField("age")
    private Integer age;

    @TableField("class_id")
    private Integer classId;

    @TableField("grade_id")
    private Integer gradeId;



}
