package com.gijela.morpheus.pistil.tools;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.DbColumnType;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.sql.Types;
import java.util.Collections;

/**
 * 本地代码生成器，仅供开发阶段手动使用，不参与正式运行。
 */
public class CodeGenerator {

    public static void main(String[] args) {
        String projectRoot = System.getProperty("user.dir");
        String moduleName = "gijela-core-pistil";

        String dbUrl = System.getenv().getOrDefault(
                "PISTIL_GEN_DB_URL",
                "jdbc:mysql://localhost:3306/gijela_pistil?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&nullCatalogMeansCurrent=true"
        );
        String dbUsername = System.getenv().getOrDefault("PISTIL_GEN_DB_USERNAME", "root");
        String dbPassword = System.getenv().getOrDefault("PISTIL_GEN_DB_PASSWORD", "");

        FastAutoGenerator.create(dbUrl, dbUsername, dbPassword)
                .globalConfig(builder -> builder
                        .author("gijela")
                        .outputDir(projectRoot + "/src/test/java"))
                .packageConfig(builder -> builder
                        .parent("com.gijela.morpheus.pistil")
                        .entity("domain.entity")
                        .mapper("mapper")
                        .service("service")
                        .serviceImpl("service.impl")
                        .controller("controller")
                        .pathInfo(Collections.singletonMap(
                                OutputFile.xml,
                                projectRoot + "/src/main/resources/mapper"
                        )))
                .dataSourceConfig(builder -> builder.typeConvertHandler((globalConfig, typeRegistry, metaInfo) -> {
                    int typeCode = metaInfo.getJdbcType().TYPE_CODE;
                    if (typeCode == Types.SMALLINT) {
                        return DbColumnType.INTEGER;
                    }
                    return typeRegistry.getColumnType(metaInfo);
                }))
                .strategyConfig(builder -> builder
                        .addInclude(
                                "aigc_sys_audit_log",
                                "aigc_sys_dept",
                                "aigc_sys_dict_item",
                                "aigc_sys_dict_type",
                                "aigc_sys_menu",
                                "aigc_sys_post",
                                "aigc_sys_role",
                                "aigc_sys_role_menu",
                                "aigc_sys_user",
                                "aigc_sys_user_role"
                        )
                        .addTablePrefix("aigc_")
                        .entityBuilder()
                        .enableFileOverride()
                        .enableTableFieldAnnotation()
                        .mapperBuilder()
                        .enableFileOverride()
                        .serviceBuilder()
                        .enableFileOverride()
                        .controllerBuilder()
                        .enableFileOverride()
                        .enableRestStyle())
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }
}