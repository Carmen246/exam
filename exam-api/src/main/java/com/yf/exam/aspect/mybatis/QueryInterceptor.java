package com.yf.exam.aspect.mybatis;

import com.yf.exam.modules.sys.user.dto.response.SysUserLoginDTO;
import lombok.extern.log4j.Log4j2;
import net.sf.jsqlparser.parser.CCJSqlParserManager;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.shiro.SecurityUtils;

import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Properties;

/**
 * 查询拦截器，用于拦截处理通用的信息、如用户ID、多租户信息等
 * @author bool
 */
@Log4j2
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),})
public class QueryInterceptor implements Interceptor {

    private static final String USER_FILTER = "{{userId}}";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        try {
            StatementHandler statementHandler = (StatementHandler) realTarget(invocation.getTarget());
            MetaObject metaObject = MetaObject.forObject(statementHandler, SystemMetaObject.DEFAULT_OBJECT_FACTORY, SystemMetaObject.DEFAULT_OBJECT_WRAPPER_FACTORY, new DefaultReflectorFactory());
            MappedStatement mappedStatement = getMappedStatement(metaObject);
            if (mappedStatement != null && SqlCommandType.SELECT == mappedStatement.getSqlCommandType()) {
                String sql = statementHandler.getBoundSql().getSql();
                if (StringUtils.contains(sql, USER_FILTER)) {
                    String outSql = this.parseSql(sql);
                    setBoundSql(metaObject, outSql);
                }
            }
        } catch (Exception e) {
            log.warn("QueryInterceptor process failed, use original sql.", e);
        }

        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {

    }

    private Object realTarget(Object target) {
        if (Proxy.isProxyClass(target.getClass())) {
            MetaObject metaObject = SystemMetaObject.forObject(target);
            return realTarget(metaObject.getValue("h.target"));
        }
        return target;
    }

    private MappedStatement getMappedStatement(MetaObject metaObject) {
        if (metaObject.hasGetter("delegate.mappedStatement")) {
            return (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        }
        if (metaObject.hasGetter("mappedStatement")) {
            return (MappedStatement) metaObject.getValue("mappedStatement");
        }
        return null;
    }

    private void setBoundSql(MetaObject metaObject, String sql) {
        if (metaObject.hasGetter("delegate.boundSql")) {
            metaObject.setValue("delegate.boundSql.sql", sql);
            return;
        }
        if (metaObject.hasGetter("boundSql")) {
            metaObject.setValue("boundSql.sql", sql);
        }
    }

    private SysUserLoginDTO getLoginUser() {
        try {
            return SecurityUtils.getSubject().getPrincipal() != null ? (SysUserLoginDTO) SecurityUtils.getSubject().getPrincipal() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String processUserId(String sql) {
        SysUserLoginDTO user = this.getLoginUser();
        if (user == null) {
            return null;
        }
        String userId = user.getId();
        if (StringUtils.isNotBlank(userId)) {
            return sql.replace(USER_FILTER, userId);
        }
        return null;
    }

    private String parseSql(String src) {
        CCJSqlParserManager parserManager = new CCJSqlParserManager();
        try {
            Select select = (Select) parserManager.parse(new StringReader(src));
            PlainSelect selectBody = (PlainSelect) select.getSelectBody();
            String sql = selectBody.toString();
            sql = this.processUserId(sql);
            return sql != null ? sql : src;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return src;
    }
}
