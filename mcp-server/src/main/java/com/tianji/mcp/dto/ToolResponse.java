package com.tianji.mcp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ToolResponse {

    private boolean success;
    private Object data;
    private String error;

    public static ToolResponse ok(Object data) {
        return new ToolResponse(true, data, null);
    }

    public static ToolResponse fail(String error) {
        return new ToolResponse(false, null, error);
    }
}
