package com.pethome.rag;

import com.pethome.entity.KnowledgeDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 服务索引客户端 - 空实现（预留）
 *
 * <p>当前 RAG 服务（pet-home-service/rag-service）尚未提供重建索引接口，故此处先空实现并直接返回成功，
 * 以便知识库重建索引的异步链路可端到端验证。</p>
 *
 * <p>接入真实 RAG 服务时：新增一个 {@link RagIndexClient} 实现类（HTTP/RPC 调用真实接口），
 * 并删除本类即可，业务代码无需改动。</p>
 *
 * @author pet-home
 */
@Slf4j
@Component
public class StubRagIndexClient implements RagIndexClient {

    @Override
    public boolean rebuildIndex(List<KnowledgeDoc> docs) {
        // TODO 调用真实 RAG 服务重建索引接口（入参可传文档 id/title/category/content），失败返回 false
        log.info("[RAG-预留] 收到重建索引请求，参与索引的启用文档数={}，当前为空实现，直接按成功处理",
                docs == null ? 0 : docs.size());
        return true;
    }

}
