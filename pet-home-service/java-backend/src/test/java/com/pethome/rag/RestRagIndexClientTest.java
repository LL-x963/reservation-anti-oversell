package com.pethome.rag;

import com.pethome.common.exception.KnowledgeServiceException;
import com.pethome.entity.KnowledgeDoc;
import com.pethome.rag.dto.RagReindexResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RAG 索引客户端实现测试（纯单元测试，不启动 Spring 容器）
 *
 * @author pet-home
 */
class RestRagIndexClientTest {

    private KnowledgeClient knowledgeClient;
    private RestRagIndexClient restRagIndexClient;

    @BeforeEach
    void setUp() {
        knowledgeClient = mock(KnowledgeClient.class);
        restRagIndexClient = new RestRagIndexClient(knowledgeClient);
    }

    @Test
    void shouldReturnTrueWhenRagCallSucceeds() {
        when(knowledgeClient.rebuildIndex(any())).thenReturn(new RagReindexResponse());

        assertThat(restRagIndexClient.rebuildIndex(List.of(doc()))).isTrue();
    }

    @Test
    void shouldReturnFalseWhenRagCallFails() {
        when(knowledgeClient.rebuildIndex(any())).thenThrow(new KnowledgeServiceException());

        assertThat(restRagIndexClient.rebuildIndex(List.of(doc()))).isFalse();
    }

    private KnowledgeDoc doc() {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(1L);
        doc.setTitle("猫咪护理");
        doc.setContent("每周梳毛2-3次。");
        return doc;
    }

}
