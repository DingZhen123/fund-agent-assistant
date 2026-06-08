package com.fundagent.mcpserver.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Map;

@Slf4j
@Component
public class AnnotationMcpToolProvider implements SmartInitializingSingleton {
    private final ApplicationContext applicationContext;
    private final McpToolRegistry toolRegistry;

    public AnnotationMcpToolProvider(ApplicationContext applicationContext, McpToolRegistry toolRegistry) {
        this.applicationContext = applicationContext;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, Object> beans = applicationContext.getBeansOfType(Object.class);
        beans.values().forEach(this::registerBeanTools);
        log.info("MCP annotation tool provider initialized: tools={}", toolRegistry.toolNames());
    }

    private void registerBeanTools(Object bean) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        ReflectionUtils.doWithMethods(targetClass, method -> registerMethodTool(bean, method),
                method -> method.isAnnotationPresent(McpTool.class));
    }

    private void registerMethodTool(Object bean, Method method) {
        McpTool annotation = method.getAnnotation(McpTool.class);
        validateMethod(method, annotation);
        ReflectionUtils.makeAccessible(method);

        McpToolDefinition definition = McpToolDefinition.builder()
                .name(annotation.name())
                .description(annotation.description())
                .inputSchema(JSON.parseObject(annotation.inputSchemaJson()))
                .build();
        toolRegistry.register(McpRegisteredTool.builder()
                .definition(definition)
                .executor(args -> invokeTool(bean, method, args))
                .build());
    }

    private void validateMethod(Method method, McpTool annotation) {
        if (annotation.name().isBlank()) {
            throw new IllegalArgumentException("@McpTool name is required: " + method);
        }
        if (method.getParameterCount() > 1) {
            throw new IllegalArgumentException("@McpTool method supports zero or one parameter: " + method);
        }
        if (method.getParameterCount() == 1 && !Map.class.isAssignableFrom(method.getParameterTypes()[0])) {
            throw new IllegalArgumentException("@McpTool method parameter must be Map<String, Object>: " + method);
        }
        try {
            JSON.parseObject(annotation.inputSchemaJson());
        } catch (Exception e) {
            throw new IllegalArgumentException("@McpTool inputSchemaJson is invalid: " + method, e);
        }
    }

    private McpToolCallResult invokeTool(Object bean, Method method, Map<String, Object> args) {
        try {
            Object result = method.getParameterCount() == 0
                    ? method.invoke(bean)
                    : method.invoke(bean, args);
            return McpToolCallResult.ok(result);
        } catch (Exception e) {
            log.error("MCP tool invoke failed: method={}", method, e);
            return McpToolCallResult.error("TOOL_INVOKE_FAILED", e.getMessage());
        }
    }
}
