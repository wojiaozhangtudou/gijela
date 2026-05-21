# API 统一约定（返回体、分页/排序、时间、幂等/批量、HTTP 状态码）

本文件规定后端接口的统一契约，作为开发与联调的唯一依据。

## 1. 统一返回体 ApiResponse
- 结构：
  - code: int，业务码。0 表示成功，非 0 表示业务失败。
  - msg: string，给前端/用户看的提示语，需支持 i18n。
  - data: any，业务数据。失败时可以为空或附加错误上下文。
  - timestamp: long，服务器毫秒时间戳。
  - traceId: string，请求链路 ID（由 MDC 注入）。
- 约定：
  - HTTP 层表达协议状态（如 401/403/422/500），业务层用 code 表达业务语义。
  - 所有接口统一返回 200（除鉴权/参数等标准场景外），但推荐遵循下文“HTTP 状态码使用规范”。

### 1.1 返回对象选择（Entity vs VO）
- 默认推荐返回 VO（视图对象），以便裁剪无关字段、做脱敏与枚举映射。
- 若该接口对返回字段“无裁剪/无敏感信息/无循环引用”限制，可直接返回实体类 Entity：
  - 示例：Page<SysMenu> 或 SysMenu 直接作为 data 返回。
- 注意：
  - 严禁直接返回包含密码、密钥、令牌、内部备注等敏感字段的实体。
  - 直接返回实体时，实体字段需具备中文 @Schema 注释；如后续出现字段控制需求，应切回 VO。

### 1.2 方法与参数封装规则
- 参数数量 ≤ 2 且为简单查询（如分页 current/size）时，允许使用 GET + @RequestParam。
- 参数数量 > 2 时，必须封装为 DTO 并使用 POST + @RequestBody（JSON）。
- 统一不使用 PathVariable，路径保持资源 + 动作风格（例如 /api/v1/xxx/page、/api/v1/xxx/delete）。

## 2. 分页与排序（详见 doc/分页排序.md）
- 请求参数（GET: RequestParam；POST: DTO 字段）：
  - current: int，>=1，默认 1（原 pageNo 兼容解析为 current）。
  - size: int，1..200，默认 20（原 pageSize 兼容解析为 size）。
  - sortField: string，排序字段，必须在白名单内（由各接口枚举定义）。
  - sortOrder: string，ASC 或 DESC，默认 DESC（兼容 ascend/descend -> ASC/DESC）。
- 条件构建：
  - 使用 @QueryField(column, op=EQ|LIKE, trim=true) 声明字段到数据库列映射。
  - `QueryWrapperBuilder.apply(dto, wrapper)` 通过反射扫描 DTO 自动拼装 where。
- 排序实现：
  - `PageSortHelper.build(current,size,sortField,sortOrder,columnResolver,fallbackColumn)` 统一构造 Page 并添加排序。
  - columnResolver 由调用方提供（通常是枚举 SysXxxSortField.from(name).column()）。
  - 若 sortField 为空或不在白名单，使用 fallbackColumn DESC（保证结果稳定性）。
- 返回：统一 `ApiResponse<Page<T>>`；无裁剪需求可直接返回实体 Page<Entity>。
- 校验：sortField / sortOrder 通过正则或枚举校验（JSR-380）；越界 size 拦截为 422。
- 兜底：必须提供 fallbackColumn（建议 create_time 或 id）。

## 3. 时间与序列化
- 时区：服务器与序列化统一使用 UTC+8。
- 时间格式：ISO8601（yyyy-MM-dd'T'HH:mm:ss.SSSXXX），例如 2025-08-21T10:20:30.123+08:00。
- Long 序列化：以字符串输出以避免 JS 精度丢失。
- BigDecimal：保留原精度，禁止使用 double 参与金额计算。
- JSON 风格：
  - 属性命名驼峰（camelCase）。
  - 空字段是否输出由全局配置统一（推荐忽略 null）。
  - 未知字段反序列化时忽略。

## 4. 幂等性（写操作）
- 头部：Idempotency-Key（客户端生成的全局唯一 key，例如 UUID）。
- 覆盖范围：新增/编辑/分配权限/重置密码/批量删除等写操作。
- 存储：Redis 键 idemp:op:{key}，TTL 10 分钟。首次请求写入并执行，重复请求直接返回首次结果。
- 冲突：如业务主键已存在等导致无法满足幂等，返回 409 + 业务码 3002（资源冲突）。

## 5. 批量语义
- 请求体统一：{"ids": [Long]}
- 入参校验：1..100 条；重复 ID 自动去重；不存在的 ID 行为：忽略并在结果中标记 failed。
- 推荐返回：
  - { total, success, failed: [{ id, reason }] }
- 事务策略：默认单条循环+部分成功，若需全有或全无需在接口文档显式声明。

## 6. HTTP 状态码使用规范
- 200 OK：业务成功（code=0）。
- 201 Created：如未来引入标准 REST 创建语义时可用；当前统一 200。
- 400 Bad Request：通用请求错误（参数缺失/格式错误）。
- 401 Unauthorized：未登录或 Token 无效/过期（结合业务码 2001/2002）。
- 403 Forbidden：已登录但无权限（业务码 2003）。
- 404 Not Found：资源不存在（业务码 3001）。
- 409 Conflict：资源冲突（业务码 3002）。
- 422 Unprocessable Entity：JSR-380 校验失败（业务码 1002）。
- 429 Too Many Requests：限流/频控（业务码 3004）。
- 500 Internal Server Error：系统异常（业务码 5000）。
- 502/504：下游异常/超时（业务码 5002）。

## 7. 校验与异常映射
- Bean Validation（JSR-380）：@NotBlank/@Size/@Pattern 等；失败 -> 422 + code=1002，msg 为首条或合并提示。
- BizException：按异常内置 code/msg 返回，通常 200（可选 400/409/422，视语义）。
- 常见异常映射：
  - AccessDeniedException -> 403 + 2003
  - AuthenticationException/JwtException -> 401 + 2001/2002
  - NoSuchElementException -> 404 + 3001
  - DuplicateKeyException -> 409 + 3002
  - IllegalArgumentException -> 400 + 1001
  - Exception 兜底 -> 500 + 5000

## 8. 软删除与审计字段
- 公共字段：id、createdAt、createdBy、updatedAt、updatedBy、deleted(delFlag)、version。
- 软删除过滤：所有查询默认带 delFlag=0 过滤。批量删除为逻辑删除，需记录操作人和时间。

## 9. JSON 示例（简化）
- 成功：
  {
    "code": 0,
    "msg": "OK",
    "data": {"id": "123"},
    "timestamp": 1734828800123,
    "traceId": "a1b2c3"
  }
- 失败：
  {
    "code": 3002,
    "msg": "角色编码已存在",
    "data": null,
    "timestamp": 1734828800123,
    "traceId": "a1b2c3"
  }

## 10. 排序白名单示例
- /api/v1/users: sortField in {"id","username","createdAt"}; sortOrder in {ASC, DESC}
- /api/v1/roles: sortField in {"id","roleCode","createdAt"}; sortOrder in {ASC, DESC}
- /api/v1/menus: sortField in {"id","name","createTime","sort"}; fallback = create_time DESC

## 11. 负载限制
- 单请求体最大 1MB；列表接口 size 最大 200；审计日志导出需改为异步（后续版本）。

## 12. 依赖注入规范（Controller / Service / 其它 Bean）
- 推荐方式：`@Autowired` 字段注入（或必要时 Setter 注入 + `@Autowired`）。
- 不推荐：构造方法注入（在多层级、双向协作较多的场景中放大循环依赖风险，当前项目以低摩擦开发效率为优先）。
- 禁止：手动 `new` 实例管理 Bean（将绕过 AOP、事务、校验等容器增强）。
- 循环依赖处理策略：
  1. 首选“拆分职责”/提取 Facade 或中介服务，消除双向直接依赖。
  2. 事件驱动：发布/订阅（ApplicationEventPublisher）解耦调用链。
  3. 确实需要延迟引用：在少数场景使用 `@Lazy` 注入（尽量局部且有注释）。
- 事务建议：在 Service 层由外层（调用入口）发起事务，避免互相调用导致的隐藏嵌套；若需跨 Service 内部协作，提取聚合/编排 Service。
- AOP / 代理注意：避免在同类内部自调用期望触发事务/缓存等切面；若需要，提取到独立 Bean。

## 13. 性能与优化
- 查询优化：尽量使用分页查询，避免一次性加载过多数据。
- 缓存：可缓存的结果尽量使用缓存（如 Redis），减少数据库压力。
- 异步处理：非关键路径的逻辑尽量异步化（如邮件发送、消息通知等）。

## 14. 安全规范
- 输入校验：所有外部输入必须校验，避免 SQL 注入、XSS 等攻击。
- 输出编码：对外输出的数据必须编码，避免 HTML 注入等攻击。
- 认证授权：敏感操作必须校验用户权限，避免越权操作。

## 15. 接口文档
- 所有接口必须有文档说明，包括请求参数、返回结果、错误码等。
- 文档必须与代码保持一致，避免出现文档与实现不符的情况。

## 16. 日志规范
- 统一使用 SLF4J API，禁止使用 Lombok `@Slf4j` 生成 Logger。
- 每个需要日志的类手写：
  ```java
  private static final Logger log = LoggerFactory.getLogger(当前类.class);
  ```
- 日志级别约定：
  - INFO：核心流程节点、启动参数、关键业务成功结果摘要
  - WARN：可恢复业务异常/外部系统短暂失败/参数边界情况
  - ERROR：不可恢复异常（打印堆栈），避免重复多层日志放大
  - DEBUG：调试信息（默认生产关闭）
  - TRACE：极细粒度，默认禁止
- 参数/数据脱敏：手机号、邮箱、令牌、密码、密钥等需做掩码（如中间 4~6 位 * 号）。
- 禁止直接输出：完整 SQL（除调试环境）、大文本（>2KB）、敏感凭据、明文密码。
- 推荐使用占位符：`log.info("user login ok, uid={} ip={}", userId, ip);`，避免字符串拼接。
- 异常记录：`log.error("update user failed, id={}", id, ex);` （最后一个参数是 Throwable）。
- 追踪 ID：依赖 TraceIdFilter 注入 `MDC`，日志布局中输出 `%X{traceId}`。
- 高频循环内慎用日志，必要时加采样或限次计数。

## 附录：实现示例（关键片段）
> 以下片段控制在 40 行以内，展示规范落地方式，真实代码可适度完善。

### A1. ApiResponse 工厂
```java
public final class ApiResponse<T> {
  private int code; private String msg; private T data; private long timestamp; private String traceId;
  private ApiResponse(int c,String m,T d){ this.code=c; this.msg=m; this.data=d; this.timestamp=System.currentTimeMillis(); this.traceId=MDC.get("traceId"); }
  public static <T> ApiResponse<T> ok(T data){ return new ApiResponse<>(0, "OK", data); }
  public static <T> ApiResponse<T> fail(int code,String msg){ return new ApiResponse<>(code, msg, null); }
  public static <T> ApiResponse<T> of(boolean success, T data, String failMsg){ return success?ok(data):fail(3002, failMsg); }
}
```

### A2. GlobalExceptionHandler（核心）
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValid(MethodArgumentNotValidException e){
    String msg = e.getBindingResult().getFieldErrors().stream()
      .map(f -> f.getField()+" "+f.getDefaultMessage()).collect(Collectors.joining("; "));
    return ResponseEntity.unprocessableEntity().body(ApiResponse.fail(1002, "参数校验失败: "+msg));
  }
  @ExceptionHandler(BizException.class)
  public ApiResponse<Void> handleBiz(BizException e){ return ApiResponse.fail(e.getCode(), e.getMessage()); }
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException e){ return ResponseEntity.status(403).body(ApiResponse.fail(2003, "无权限")); }
  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiResponse<Void>> auth(AuthenticationException e){ return ResponseEntity.status(401).body(ApiResponse.fail(2001, "未认证")); }
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> other(Exception e){ log.error("unhandled ex", e); return ResponseEntity.internalServerError().body(ApiResponse.fail(5000, "系统错误")); }
}
```

### A3. Controller 标准模板
```java
@Tag(name="用户管理")
@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {
  @Autowired private UserService userService;

  @Operation(summary="用户分页")
  @PostMapping("/page")
  @PreAuthorize("hasAuthority('user:list')")
  public ApiResponse<Page<UserVO>> page(@Valid @RequestBody UserPageDTO dto){
     QueryWrapper<User> w = new QueryWrapper<>();
     QueryWrapperBuilder.apply(dto,w);
     Page<User> page = PageSortHelper.build(dto, UserSortField::resolve, "create_time DESC");
     return ApiResponse.ok(page.convert(this::toVO));
  }
}
```

### A4. 错误响应示例（再次引用）
```json
{"code":1002,"msg":"参数校验失败: username 不能为空","data":null,"timestamp":1711111111111,"traceId":"t1"}
```

### A5. 批量删除响应结构（建议）
```json
{"code":0,"msg":"OK","data":{"total":5,"success":4,"failed":[{"id":101,"reason":"不存在"}]}}
```

---
（附录结束）
