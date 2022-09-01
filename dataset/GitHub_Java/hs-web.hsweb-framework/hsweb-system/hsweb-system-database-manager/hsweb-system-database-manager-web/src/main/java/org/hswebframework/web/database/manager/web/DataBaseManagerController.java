package org.hswebframework.web.database.manager.web;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.hswebframework.web.Sqls;
import org.hswebframework.web.authorization.Permission;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.controller.message.ResponseMessage;
import org.hswebframework.web.database.manager.DatabaseManagerService;
import org.hswebframework.web.database.manager.SqlExecuteRequest;
import org.hswebframework.web.database.manager.SqlExecuteResult;
import org.hswebframework.web.database.manager.SqlInfo;
import org.hswebframework.web.database.manager.meta.ObjectMetadata;
import org.hswebframework.web.database.manager.sql.TransactionInfo;
import org.hswebframework.web.datasource.DataSourceHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/database/manager")
@Api(value = "开发人员工具-数据库维护", description = "数据库维护")
@Authorize(permission = "database-manager",description = "数据库维护")
public class DataBaseManagerController {

    @Autowired
    private DatabaseManagerService databaseManagerService;

    @GetMapping("/metas")
    @Authorize(action = Permission.ACTION_QUERY)
    @ApiOperation("获取数据库元数据")
    public ResponseMessage<Map<ObjectMetadata.ObjectType, List<? extends ObjectMetadata>>> parseAllObject() throws Exception {
        return parseAllObject(null);
    }

    @GetMapping("/metas/{datasourceId}")
    @Authorize(action = Permission.ACTION_QUERY)
    @ApiOperation("获取指定数据源的元数据")
    public ResponseMessage<Map<ObjectMetadata.ObjectType, List<? extends ObjectMetadata>>> parseAllObject(@PathVariable String datasourceId) throws Exception {
        DataSourceHolder.switcher().use(datasourceId);
        return ResponseMessage.ok(databaseManagerService.getMetas());
    }

    @PostMapping("/execute/{datasourceId}")
    @Authorize(action = "execute", description = "执行SQL")
    @ApiOperation("指定数据源执行SQL")
    public ResponseMessage<List<SqlExecuteResult>> execute(
            @PathVariable String datasourceId
            , @RequestBody String sqlLines) throws Exception {
        DataSourceHolder.switcher().use(datasourceId);
        return ResponseMessage.ok(databaseManagerService
                .execute(SqlExecuteRequest.builder()
                        .sql(parseSql(sqlLines))
                        .build()));

    }

    @PostMapping("/execute")
    @ApiOperation("执行SQL")
    @Authorize(action = "execute", description = "执行SQL")
    public ResponseMessage<List<SqlExecuteResult>> execute(@RequestBody String sqlLines) throws Exception {
        return ResponseMessage.ok(databaseManagerService
                .execute(SqlExecuteRequest.builder()
                        .sql(parseSql(sqlLines))
                        .build()));
    }

    @PostMapping("/transactional/execute/{transactionalId}")
    @Authorize(action = "execute", description = "执行SQL")
    @ApiOperation("开启事务执行SQL")
    public ResponseMessage<List<SqlExecuteResult>> executeTransactional(@PathVariable String transactionalId, @RequestBody String sqlLines) throws Exception {
        return ResponseMessage.ok(databaseManagerService
                .execute(transactionalId,SqlExecuteRequest.builder()
                        .sql(parseSql(sqlLines))
                        .build()));
    }

    @GetMapping("/transactional/new")
    @Authorize(action = "execute", description = "执行SQL")
    @ApiOperation("新建事务")
    public ResponseMessage<String> newTransaction() throws Exception {
        return ResponseMessage.ok(databaseManagerService.newTransaction());
    }

    @GetMapping("/transactional")
    @Authorize(action = "execute", description = "执行SQL")
    @ApiOperation("获取全部事务信息")
    public ResponseMessage<List<TransactionInfo>> allTransaction() throws Exception {
        return ResponseMessage.ok(databaseManagerService.allTransaction());
    }

    @PostMapping("/transactional/{id}/commit")
    @Authorize(action = "execute", description = "执行SQL")
    @ApiOperation("提交事务")
    public ResponseMessage<String> commitTransaction(@PathVariable String id) throws Exception {
        databaseManagerService.commit(id);
        return ResponseMessage.ok();
    }

    @PostMapping("/transactional/{id}/rollback")
    @Authorize(action = "execute", description = "执行SQL")
    @ApiOperation("回滚事务")
    public ResponseMessage<String> rollbackTransaction(@PathVariable String id) throws Exception {
        databaseManagerService.rollback(id);
        return ResponseMessage.ok();
    }

    private List<SqlInfo> parseSql(String sqlText) {
        List<String> sqlList = Sqls.parse(sqlText);
        return sqlList.stream().map(sql -> {
            SqlInfo sqlInfo = new SqlInfo();
            sqlInfo.setSql(sql);
            sqlInfo.setType(sql.split("[ ]")[0].toLowerCase());
            return sqlInfo;
        }).collect(Collectors.toList());
    }

}
