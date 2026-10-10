package com.pethome.rag.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 传给 RAG 服务的宠物信息
 *
 * <p>rag-service 的 PetInfo 允许扩展字段，会按字段名拼进 Prompt 的【宠物信息】。</p>
 *
 * @author pet-home
 */
@Data
public class RagPetInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 品种 */
    private String breed;

    /** 年龄（岁），不足 1 岁时不传，改用 ageMonths */
    private Integer age;

    /** 月龄，仅不足 1 岁时传 */
    private Integer ageMonths;

    /** 体重(kg) */
    private BigDecimal weight;

    /** 名字 */
    private String name;

    /** 性别：公 / 母（数据库 0未知 时不传） */
    private String gender;

}
