package com.example.incident.port;

import java.util.List;

public interface TextEmbeddingModel {
    String modelName();
    List<Float> embed(String text);
}
