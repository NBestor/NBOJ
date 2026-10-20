package com.onlinejudge.learning.knowledge.application;

import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNode;
import com.onlinejudge.learning.knowledge.dto.InformaticsKnowledgeNodeDetailResponse;
import com.onlinejudge.learning.knowledge.dto.InformaticsKnowledgeNodeResponse;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import com.onlinejudge.learning.standardlibrary.dto.AiStandardLibraryItemResponse;
import com.onlinejudge.learning.standardlibrary.persistence.AiStandardLibraryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InformaticsKnowledgeService {

    private final InformaticsKnowledgeNodeRepository knowledgeRepository;
    private final AiStandardLibraryItemRepository standardLibraryRepository;

    @Transactional(readOnly = true)
    public List<InformaticsKnowledgeNodeResponse> tree(boolean includeDisabled) {
        List<InformaticsKnowledgeNode> nodes = includeDisabled
                ? knowledgeRepository.findAllByOrderByPathAscSortOrderAscCodeAsc()
                : knowledgeRepository.findByEnabledTrueOrderByPathAscSortOrderAscCodeAsc();
        return buildTree(nodes);
    }

    @Transactional(readOnly = true)
    public InformaticsKnowledgeNodeDetailResponse detail(String code) {
        InformaticsKnowledgeNode node = find(code);
        List<InformaticsKnowledgeNode> all = knowledgeRepository.findAllByOrderByPathAscSortOrderAscCodeAsc();
        List<InformaticsKnowledgeNodeResponse> ancestors = ancestors(node, all);
        List<InformaticsKnowledgeNodeResponse> children = all.stream()
                .filter(candidate -> code.equals(candidate.getParentCode()))
                .sorted(nodeComparator())
                .map(InformaticsKnowledgeNodeResponse::from)
                .toList();
        List<AiStandardLibraryItemResponse> items = standardLibraryItems(code);
        return InformaticsKnowledgeNodeDetailResponse.builder()
                .node(InformaticsKnowledgeNodeResponse.from(node))
                .ancestors(ancestors)
                .children(children)
                .standardLibraryItems(items)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AiStandardLibraryItemResponse> standardLibraryItems(String code) {
        find(code);
        return standardLibraryRepository
                .findByEnabledTrueAndKnowledgeNodeCodesContainingOrderByLayerAscCategoryAscCodeAsc(code)
                .stream()
                .map(AiStandardLibraryItemResponse::from)
                .toList();
    }

    private InformaticsKnowledgeNode find(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("知识点编码不能为空");
        }
        return knowledgeRepository.findByCode(code.trim())
                .orElseThrow(() -> new IllegalArgumentException("知识节点不存在: " + code));
    }

    private List<InformaticsKnowledgeNodeResponse> buildTree(List<InformaticsKnowledgeNode> nodes) {
        Map<String, InformaticsKnowledgeNodeResponse> byCode = new LinkedHashMap<>();
        for (InformaticsKnowledgeNode node : nodes.stream().sorted(nodeComparator()).toList()) {
            byCode.put(node.getCode(), InformaticsKnowledgeNodeResponse.from(node));
        }
        List<InformaticsKnowledgeNodeResponse> roots = new ArrayList<>();
        for (InformaticsKnowledgeNode node : nodes.stream().sorted(nodeComparator()).toList()) {
            InformaticsKnowledgeNodeResponse response = byCode.get(node.getCode());
            InformaticsKnowledgeNodeResponse parent = byCode.get(node.getParentCode());
            if (parent == null) {
                roots.add(response);
            } else {
                parent.getChildren().add(response);
            }
        }
        sortChildren(roots);
        return roots;
    }

    private List<InformaticsKnowledgeNodeResponse> ancestors(InformaticsKnowledgeNode node,
                                                             List<InformaticsKnowledgeNode> all) {
        Map<String, InformaticsKnowledgeNode> byCode = new LinkedHashMap<>();
        for (InformaticsKnowledgeNode candidate : all) {
            byCode.put(candidate.getCode(), candidate);
        }
        List<InformaticsKnowledgeNodeResponse> result = new ArrayList<>();
        InformaticsKnowledgeNode current = node;
        while (current.getParentCode() != null && byCode.containsKey(current.getParentCode())) {
            current = byCode.get(current.getParentCode());
            result.add(0, InformaticsKnowledgeNodeResponse.from(current));
        }
        return result;
    }

    private void sortChildren(List<InformaticsKnowledgeNodeResponse> nodes) {
        nodes.sort(Comparator.comparingInt(InformaticsKnowledgeNodeResponse::getSortOrder)
                .thenComparing(InformaticsKnowledgeNodeResponse::getCode));
        for (InformaticsKnowledgeNodeResponse node : nodes) {
            sortChildren(node.getChildren());
        }
    }

    private Comparator<InformaticsKnowledgeNode> nodeComparator() {
        return Comparator.comparingInt(InformaticsKnowledgeNode::getSortOrder)
                .thenComparing(InformaticsKnowledgeNode::getCode);
    }
}
