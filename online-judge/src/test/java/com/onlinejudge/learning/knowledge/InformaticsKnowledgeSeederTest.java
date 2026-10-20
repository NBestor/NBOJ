package com.onlinejudge.learning.knowledge;

import com.onlinejudge.learning.knowledge.application.InformaticsKnowledgeSeeder;
import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNodeType;
import com.onlinejudge.learning.knowledge.persistence.InformaticsKnowledgeNodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:informatics-knowledge-seeder;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "TEACHER_PASSWORD=test-teacher-password",
        "TEACHER_SESSION_SECRET=test-teacher-session-secret-1234567890",
        "STUDENT_TOKEN_SECRET=test-student-token-secret-1234567890",
        "AI_ENABLED=false",
        "app.content-migration.enabled=true"
})
class InformaticsKnowledgeSeederTest {

    @Autowired
    InformaticsKnowledgeNodeRepository repository;

    @Autowired
    InformaticsKnowledgeSeeder seeder;

    @Test
    void seedsBroadFineGrainedInformaticsKnowledgeTreeIdempotently() {
        long initialCount = repository.count();

        assertThat(initialCount).isGreaterThanOrEqualTo(720);
        assertThat(repository.findAll().stream()
                .filter(node -> node.getType() == InformaticsKnowledgeNodeType.DOMAIN)
                .count()).isGreaterThanOrEqualTo(6);
        assertThat(repository.findAll().stream()
                .filter(node -> node.getType() == InformaticsKnowledgeNodeType.KNOWLEDGE_POINT)
                .count()).isGreaterThanOrEqualTo(575);
        assertThat(repository.findByCode("BASIC.LOOP.BOUNDARY.左闭右开")).isPresent();
        assertThat(repository.findByCode("ALGO.DP.STATE.状态含义")).isPresent();
        assertThat(repository.findByCode("CONTEST.READING.CONSTRAINT.数据范围")).isPresent();
        assertThat(repository.findByCode("BASIC.ARRAY.UPDATE.读旧写新分离")).isPresent();
        assertThat(repository.findByCode("BASIC.STRING.MATCH.未找到结果哨兵")).isPresent();
        assertThat(repository.findByCode("DS.GRAPH.STORE.多组图清空")).isPresent();
        assertThat(repository.findByCode("ALGO.SIM.CORNER.并列规则冲突")).isPresent();
        assertThat(repository.findByCode("ENG.COMPLEXITY.TRADEOFF.预处理收益判断")).isPresent();
        assertThat(repository.findByCode("CONTEST.SUBMIT.CHECKLIST.溢出风险复查")).isPresent();
        assertThat(repository.findByCode("ALGO.SORT.BASIC.冒泡排序")).isPresent();
        assertThat(repository.findByCode("ALGO.SORT.BASIC.选择排序")).isPresent();
        assertThat(repository.findByCode("ALGO.SORT.BASIC.计数排序")).isPresent();
        assertThat(repository.findByCode("BASIC.ARRAY.TRAVERSE.擂台法求最值")).isPresent();
        assertThat(repository.findByCode("BASIC.STRING.BUILD.游程编码")).isPresent();
        assertThat(repository.findByCode("ALGO.SIM.STATE.状态标记法")).isPresent();
        assertThat(repository.findByCode("ALGO.SIM.STATE.多数投票算法")).isPresent();
        assertThat(repository.findByCode("ALGO.SORT.APPLICATION.分组排序")).isPresent();

        var updateNode = repository.findByCode("BASIC.ARRAY.UPDATE.读旧写新分离").orElseThrow();
        assertThat(updateNode.getParentCode()).isEqualTo("BASIC.ARRAY.UPDATE");
        assertThat(updateNode.getPath()).contains("数组更新", "读旧写新分离");
        assertThat(updateNode.getDescription()).contains("细颗粒知识点");
        assertThat(updateNode.getLearningObjectives()).contains("能解释读旧写新分离的含义。");
        assertThat(updateNode.getTypicalProblems()).contains("读旧写新分离相关调试样例");

        var linkedList = repository.findByCode("DS.LINEAR.LIST").orElseThrow();
        assertThat(linkedList.getName()).isEqualTo("链表");
        assertThat(linkedList.getAliases()).contains("链式思想", "linked list");

        var interval = repository.findByCode("ALGO.GREEDY.INTERVAL").orElseThrow();
        assertThat(interval.getName()).isEqualTo("区间调度与合并");
        assertThat(interval.getAliases()).contains("区间贪心", "interval scheduling");

        var rle = repository.findByCode("BASIC.STRING.BUILD.游程编码").orElseThrow();
        assertThat(rle.getAliases()).contains("运行长度编码", "RLE", "数据压缩");

        var arena = repository.findByCode("BASIC.ARRAY.TRAVERSE.擂台法求最值").orElseThrow();
        assertThat(arena.getAliases()).contains("最值维护", "打擂台");

        seeder.run();

        assertThat(repository.count()).isEqualTo(initialCount);
    }
}
