package com.tianji.aichat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponse {

    private String sessionId;
    private String reply;
    private List<Map<String, Object>> products;
}
