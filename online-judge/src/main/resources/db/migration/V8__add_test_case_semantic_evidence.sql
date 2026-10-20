-- Test-case semantic evidence layer.
-- The semantic profile describes what a case covers. It is not a precomputed diagnosis.

ALTER TABLE public.test_cases
    ADD COLUMN IF NOT EXISTS semantic_code varchar(160),
    ADD COLUMN IF NOT EXISTS intent_type varchar(40),
    ADD COLUMN IF NOT EXISTS intent_title varchar(160),
    ADD COLUMN IF NOT EXISTS intent_summary varchar(800),
    ADD COLUMN IF NOT EXISTS learning_objective varchar(800),
    ADD COLUMN IF NOT EXISTS contest_role varchar(40),
    ADD COLUMN IF NOT EXISTS reveal_policy varchar(40),
    ADD COLUMN IF NOT EXISTS knowledge_node_code varchar(160),
    ADD COLUMN IF NOT EXISTS skill_unit_code varchar(160),
    ADD COLUMN IF NOT EXISTS review_status varchar(40),
    ADD COLUMN IF NOT EXISTS source_reference varchar(1200),
    ADD COLUMN IF NOT EXISTS library_version varchar(80),
    ADD COLUMN IF NOT EXISTS reviewed_at timestamp(6) without time zone;

CREATE UNIQUE INDEX IF NOT EXISTS uk_test_case_semantic_code
    ON public.test_cases (semantic_code)
    WHERE semantic_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_test_case_semantic_knowledge
    ON public.test_cases (knowledge_node_code, intent_type)
    WHERE semantic_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_test_case_semantic_skill
    ON public.test_cases (skill_unit_code, intent_type)
    WHERE semantic_code IS NOT NULL;

ALTER TABLE public.test_cases
    DROP CONSTRAINT IF EXISTS ck_test_case_semantic_all_or_none,
    ADD CONSTRAINT ck_test_case_semantic_all_or_none CHECK (
        (
            semantic_code IS NULL
            AND intent_type IS NULL
            AND intent_title IS NULL
            AND intent_summary IS NULL
            AND learning_objective IS NULL
            AND contest_role IS NULL
            AND reveal_policy IS NULL
            AND knowledge_node_code IS NULL
            AND skill_unit_code IS NULL
            AND review_status IS NULL
            AND source_reference IS NULL
            AND library_version IS NULL
            AND reviewed_at IS NULL
        )
        OR
        (
            semantic_code IS NOT NULL
            AND intent_type IS NOT NULL
            AND intent_title IS NOT NULL
            AND intent_summary IS NOT NULL
            AND learning_objective IS NOT NULL
            AND contest_role IS NOT NULL
            AND reveal_policy IS NOT NULL
            AND knowledge_node_code IS NOT NULL
            AND skill_unit_code IS NOT NULL
            AND review_status IS NOT NULL
            AND source_reference IS NOT NULL
            AND library_version IS NOT NULL
            AND reviewed_at IS NOT NULL
        )
    ),
    DROP CONSTRAINT IF EXISTS ck_test_case_semantic_intent_type,
    ADD CONSTRAINT ck_test_case_semantic_intent_type CHECK (
        intent_type IS NULL OR intent_type IN (
            'REPRESENTATIVE', 'BOUNDARY', 'EDGE_CASE', 'STRUCTURAL',
            'STATE_SPACE', 'SCALE', 'PERFORMANCE', 'ROBUSTNESS'
        )
    ),
    DROP CONSTRAINT IF EXISTS ck_test_case_semantic_contest_role,
    ADD CONSTRAINT ck_test_case_semantic_contest_role CHECK (
        contest_role IS NULL OR contest_role IN (
            'SAMPLE_EXPLANATION', 'CORRECTNESS_GUARD', 'SUBTASK_GATE', 'COMPLEXITY_STRESS'
        )
    ),
    DROP CONSTRAINT IF EXISTS ck_test_case_semantic_reveal_policy,
    ADD CONSTRAINT ck_test_case_semantic_reveal_policy CHECK (
        reveal_policy IS NULL
        OR (is_hidden = true AND reveal_policy = 'AI_GENERALIZED')
        OR (is_hidden = false AND reveal_policy = 'PUBLIC_EXAMPLE')
    ),
    DROP CONSTRAINT IF EXISTS ck_test_case_semantic_review_status,
    ADD CONSTRAINT ck_test_case_semantic_review_status CHECK (
        review_status IS NULL OR review_status IN ('DRAFT', 'REVIEWED', 'RETIRED')
    );

ALTER TABLE public.test_cases
    DROP CONSTRAINT IF EXISTS fk_test_case_semantic_knowledge,
    ADD CONSTRAINT fk_test_case_semantic_knowledge
        FOREIGN KEY (knowledge_node_code)
        REFERENCES public.informatics_knowledge_nodes(code),
    DROP CONSTRAINT IF EXISTS fk_test_case_semantic_skill,
    ADD CONSTRAINT fk_test_case_semantic_skill
        FOREIGN KEY (skill_unit_code)
        REFERENCES public.ai_standard_skill_units(code);

DROP TABLE IF EXISTS test_case_semantic_v1;
CREATE TEMP TABLE test_case_semantic_v1 (
    problem_title varchar(255) NOT NULL,
    order_index integer NOT NULL,
    semantic_code varchar(160) NOT NULL,
    intent_type varchar(40) NOT NULL,
    intent_title varchar(160) NOT NULL,
    intent_summary varchar(800) NOT NULL,
    learning_objective varchar(800) NOT NULL,
    contest_role varchar(40) NOT NULL,
    knowledge_node_code varchar(160) NOT NULL,
    skill_unit_code varchar(160) NOT NULL,
    PRIMARY KEY (problem_title, order_index),
    UNIQUE (semantic_code)
);

INSERT INTO test_case_semantic_v1 (
    problem_title, order_index, semantic_code, intent_type, intent_title,
    intent_summary, learning_objective, contest_role, knowledge_node_code, skill_unit_code
) VALUES
    ('两数求和', 0, 'TCI_SUM_REPRESENTATIVE', 'REPRESENTATIVE', '基础读入与求和',
     '覆盖两个普通整数的读取、运算和单值输出，作为最小完整解题流程的公开样例。',
     '能把题面中的两个输入量映射到变量，并按规定完成整数运算与输出。',
     'SAMPLE_EXPLANATION', 'BASIC.IO.STDIN.输入顺序映射', 'SK_IO_STRUCTURE_MAPPING'),
    ('两数求和', 1, 'TCI_SUM_SIGNED_INPUT', 'EDGE_CASE', '异号整数输入',
     '覆盖含负数的有符号整数运算，检查读取类型和符号处理是否保持一致。',
     '能正确处理有符号整数，并理解输入符号属于数值而不是分隔或格式字符。',
     'SAMPLE_EXPLANATION', 'BASIC.TYPE.INTEGER.int_范围', 'SK_INTEGER_RANGE_PROTECTION'),
    ('两数求和', 2, 'TCI_SUM_ZERO_IDENTITY', 'BOUNDARY', '零值边界',
     '覆盖两个输入都处在零值边界的情况，用于检查初始化和零的加法单位元语义。',
     '能在最小数值边界下保持表达式、变量初始化和输出流程的正确性。',
     'CORRECTNESS_GUARD', 'ENG.DEBUG.BOUNDARY.最小输入', 'SK_COMPAT_BOUNDARY_CONDITION'),
    ('两数求和', 3, 'TCI_SUM_SIGN_CANCELLATION', 'STRUCTURAL', '异号抵消关系',
     '覆盖绝对值相同、符号相反的输入关系，检查程序是否执行数值运算而非字符串拼接。',
     '能区分数值加法与文本拼接，并用输入关系验证结果应满足的代数不变量。',
     'CORRECTNESS_GUARD', 'BASIC.TYPE.INTEGER.int_范围', 'SK_INTEGER_RANGE_PROTECTION'),

    ('回文判断', 0, 'TCI_PALINDROME_ODD_MATCH', 'REPRESENTATIVE', '奇数长度回文',
     '覆盖奇数长度且左右完全对称的字符串，检查双指针从两端向中心推进的基本过程。',
     '能维护左右指针对应字符相等时的推进规则，并在中心相遇后给出正确结论。',
     'SAMPLE_EXPLANATION', 'ALGO.TWO_POINTERS.OPPOSITE.左右指针移动依据', 'SK_V8_TWO_POINTERS_OPPOSITE_INVARIANT'),
    ('回文判断', 1, 'TCI_PALINDROME_MISMATCH', 'EDGE_CASE', '中途出现不匹配',
     '覆盖字符串中存在首个不对称位置的情况，检查程序能否及时识别并保持最终状态。',
     '能定位左右字符的首次偏差，正确维护真假标志和提前结束条件。',
     'SAMPLE_EXPLANATION', 'ALGO.TWO_POINTERS.OPPOSITE.终止条件', 'SK_V8_TWO_POINTERS_OPPOSITE_INVARIANT'),
    ('回文判断', 2, 'TCI_PALINDROME_SINGLE_CHAR', 'BOUNDARY', '单字符边界',
     '覆盖长度为一的最小非空字符串，检查空循环、中心元素和默认结论的边界语义。',
     '能说明没有成对字符需要比较时为什么仍满足回文定义，并避免越界访问。',
     'CORRECTNESS_GUARD', 'BASIC.STRING.SUBSTRING.起止位置', 'SK_STRING_SLICE_ENDPOINT'),
    ('回文判断', 3, 'TCI_PALINDROME_EVEN_MATCH', 'STRUCTURAL', '偶数长度对称',
     '覆盖没有单独中心字符的偶数长度回文，检查左右指针交错时的循环终止条件。',
     '能统一处理奇偶长度，并用清晰不变量描述尚未比较的闭合区间。',
     'CORRECTNESS_GUARD', 'ALGO.TWO_POINTERS.OPPOSITE.终止条件', 'SK_V8_TWO_POINTERS_OPPOSITE_INVARIANT'),

    ('FizzBuzz', 0, 'TCI_FIZZBUZZ_COMMON_MULTIPLE', 'STRUCTURAL', '公共倍数优先级',
     '覆盖同时满足两个整除条件的输入，检查复合条件是否在单条件之前判断。',
     '能设计互斥且完整的分支顺序，避免公共倍数被较宽的单一条件提前截获。',
     'SAMPLE_EXPLANATION', 'BASIC.BRANCH.CASE.互斥条件', 'SK_BRANCH_CASE_COVERAGE'),
    ('FizzBuzz', 1, 'TCI_FIZZBUZZ_FIRST_DIVISOR', 'REPRESENTATIVE', '第一类整除分支',
     '覆盖只满足第一类整除条件的普通输入，验证对应分支和输出文本。',
     '能把一个互斥分类条件准确映射到指定输出，并保持分支覆盖不重不漏。',
     'SAMPLE_EXPLANATION', 'BASIC.BRANCH.CASE.互斥条件', 'SK_BRANCH_CASE_COVERAGE'),
    ('FizzBuzz', 2, 'TCI_FIZZBUZZ_SECOND_DIVISOR', 'REPRESENTATIVE', '第二类整除分支',
     '覆盖只满足第二类整除条件的普通输入，与第一类分支形成对照。',
     '能用对照样例检查每个分支的条件、输出内容和判断顺序是否一致。',
     'SAMPLE_EXPLANATION', 'BASIC.BRANCH.CASE.互斥条件', 'SK_BRANCH_CASE_COVERAGE'),
    ('FizzBuzz', 3, 'TCI_FIZZBUZZ_FALLTHROUGH', 'EDGE_CASE', '默认分支覆盖',
     '覆盖两个整除条件都不满足的情况，检查默认分支是否保留原始数值输出。',
     '能证明分类讨论覆盖所有输入，并为不属于特殊类别的输入保留正确行为。',
     'CORRECTNESS_GUARD', 'BASIC.BRANCH.CASE.覆盖所有情况', 'SK_BRANCH_CASE_COVERAGE'),
    ('FizzBuzz', 4, 'TCI_FIZZBUZZ_LARGER_COMMON_MULTIPLE', 'BOUNDARY', '重复周期边界',
     '覆盖更靠后的公共倍数，防止程序只对公开样例值做特判或遗漏周期性。',
     '能从整除关系而非固定样例推出分支结果，并检查规则在后续周期仍成立。',
     'CORRECTNESS_GUARD', 'BASIC.BRANCH.CASE.边界归属', 'SK_BRANCH_CASE_COVERAGE'),

    ('阶乘计算', 0, 'TCI_FACTORIAL_REPRESENTATIVE', 'REPRESENTATIVE', '普通阶乘迭代',
     '覆盖需要多次连续乘法的普通输入，检查循环区间和累计变量更新。',
     '能定义累计量在每轮后的含义，并确认所有需要相乘的整数恰好处理一次。',
     'SAMPLE_EXPLANATION', 'BASIC.LOOP.BOUNDARY.左闭右开', 'SK_LOOP_ENDPOINT_INCLUSION'),
    ('阶乘计算', 1, 'TCI_FACTORIAL_ZERO', 'BOUNDARY', '零阶乘定义边界',
     '覆盖乘法序列为空时的定义边界，检查累计量初值是否表达空乘积。',
     '能用数学定义解释零阶乘，并把边界值落实为正确的初始化与空循环行为。',
     'SAMPLE_EXPLANATION', 'ENG.DEBUG.BOUNDARY.最小输入', 'SK_COMPAT_BOUNDARY_CONDITION'),
    ('阶乘计算', 2, 'TCI_FACTORIAL_MULTI_DIGIT', 'SCALE', '多位结果范围',
     '覆盖结果进入多位整数的规模，检查累计变量类型和循环次数是否仍然正确。',
     '能估算中间结果数量级，并选择足以容纳题目范围的整数类型。',
     'CORRECTNESS_GUARD', 'BASIC.TYPE.INTEGER.int_范围', 'SK_INTEGER_RANGE_PROTECTION'),
    ('阶乘计算', 3, 'TCI_FACTORIAL_UPPER_SAFE_RANGE', 'BOUNDARY', '给定范围上界',
     '覆盖当前题目允许范围内较大的输入，用于检查端点包含和整数范围风险。',
     '能同时核对循环右端点与最大中间结果，避免少乘一次或发生整型溢出。',
     'SUBTASK_GATE', 'BASIC.TYPE.INTEGER.整型溢出', 'SK_V7_NUMERIC_RANGE_PRECISION_CONTRACT'),

    ('质数判断', 0, 'TCI_PRIME_REPRESENTATIVE', 'REPRESENTATIVE', '普通质数判定',
     '覆盖没有非平凡因子的普通质数，检查试除逻辑和最终真假结论。',
     '能用因子定义解释质数，并在没有找到因子时保持正确判定状态。',
     'SAMPLE_EXPLANATION', 'MATH.NUMBER.PRIME.试除判定', 'SK_V16_PRIME_TRIAL_DIVISION'),
    ('质数判断', 1, 'TCI_PRIME_ONE_DEFINITION', 'BOUNDARY', '一不是质数',
     '覆盖质数定义的最小特殊值，检查程序是否把小于最小质数的值单独处理。',
     '能准确陈述质数定义，并把定义边界与一般试除流程分开。',
     'SAMPLE_EXPLANATION', 'MATH.NUMBER.PRIME.1_不是质数', 'SK_NUMBER_THEORY_BOUNDARY'),
    ('质数判断', 2, 'TCI_PRIME_SMALLEST', 'BOUNDARY', '最小质数',
     '覆盖最小质数及空试除区间，检查默认状态和循环边界是否协调。',
     '能处理最小合法质数，并解释试除循环不执行时判定仍成立的原因。',
     'SAMPLE_EXPLANATION', 'MATH.NUMBER.PRIME.试除判定', 'SK_V16_PRIME_TRIAL_DIVISION'),
    ('质数判断', 3, 'TCI_PRIME_LARGE', 'PERFORMANCE', '大质数复杂度',
     '覆盖接近题目规模上界的质数，错误的线性试除会暴露不必要的操作量。',
     '能从因子成对性质推出平方根试除上界，并估算判定复杂度。',
     'COMPLEXITY_STRESS', 'MATH.NUMBER.DIVISIBILITY.平方根优化', 'SK_V13_PRIME_FACTOR_RANGE_CONTRACT'),
    ('质数判断', 4, 'TCI_PRIME_COMPOSITE', 'EDGE_CASE', '非最小因子合数',
     '覆盖具有非平凡因子的合数，检查发现因子后的状态更新和提前结束。',
     '能在找到一个合法因子后终止搜索，并避免把一般合数误判为质数。',
     'CORRECTNESS_GUARD', 'MATH.NUMBER.PRIME.试除判定', 'SK_V16_PRIME_TRIAL_DIVISION'),

    ('潮汐道路最早到达', 0, 'TCI_TIDE_ROUTE_REPRESENTATIVE', 'REPRESENTATIVE', '周期等待最短路',
     '覆盖多条道路、周期等待和替代路径共同作用的公开场景，验证时间依赖松弛。',
     '能把当前到达时间转换为可出发时间，并用新的到达时间执行最短路松弛。',
     'SAMPLE_EXPLANATION', 'ALGO.GRAPH.SHORTEST.Dijkstra', 'SK_V13_SHORTEST_DISTANCE_RELAXATION_CONTRACT'),
    ('潮汐道路最早到达', 1, 'TCI_TIDE_ROUTE_UNREACHABLE', 'STRUCTURAL', '不可达图结构',
     '覆盖目标与起点不连通的图结构，检查无穷距离初始化和不可达输出。',
     '能区分尚未松弛与确实不可达，并避免对无穷值继续执行无意义计算。',
     'CORRECTNESS_GUARD', 'ALGO.GRAPH.SHORTEST.距离初始化', 'SK_V7_GRAPH_RELAXATION_AND_TOPO_CONTRACT'),

    ('相邻石子合并最小代价', 0, 'TCI_STONE_INTERVAL_DP_REPRESENTATIVE', 'REPRESENTATIVE', '区间合并主流程',
     '覆盖多个相邻区间的不同划分方式，验证区间状态、枚举断点和前缀和代价。',
     '能定义区间 DP 状态并按区间长度递增顺序完成所有合法转移。',
     'SAMPLE_EXPLANATION', 'ALGO.DP.CLASSIC.区间_DP_入门', 'SK_V6_DP_CLASSIC_TRANSITION_ORDER'),
    ('相邻石子合并最小代价', 1, 'TCI_STONE_INTERVAL_DP_UNBALANCED', 'STRUCTURAL', '非贪心权值结构',
     '覆盖权值分布不均且局部最小合并不保证全局最优的隐藏结构。',
     '能用完整区间划分比较替代局部贪心，并检查每个子区间只依赖更短区间。',
     'SUBTASK_GATE', 'ALGO.DP.CLASSIC.区间_DP_入门', 'SK_V6_DP_CLASSIC_TRANSITION_ORDER'),

    ('课程树选课收益', 0, 'TCI_COURSE_TREE_DP_REPRESENTATIVE', 'REPRESENTATIVE', '树上依赖背包',
     '覆盖父子依赖和选择数量限制共同作用的公开场景，验证后序合并子树状态。',
     '能定义子树内选择数量的收益状态，并在合并子树时保持先选父节点的闭包。',
     'SAMPLE_EXPLANATION', 'ALGO.DP.CLASSIC.树形_DP_入门', 'SK_V7_DP_STRUCTURED_STATE_DEPENDENCY'),
    ('课程树选课收益', 1, 'TCI_COURSE_TREE_DP_NEGATIVE_PARENT', 'EDGE_CASE', '负收益依赖节点',
     '覆盖父节点收益为负但解锁高收益子节点的结构，检查不可达状态和初始化。',
     '能区分未选择、不可达与负收益状态，不用零初始化破坏依赖闭包。',
     'SUBTASK_GATE', 'ALGO.DP.INIT.边界状态', 'SK_V13_DP_STATE_TRANSITION_ORDER_CONTRACT'),

    ('可撤销道路连通性', 0, 'TCI_ROLLBACK_DSU_LIFECYCLE', 'REPRESENTATIVE', '边生命周期与回滚',
     '覆盖加边、查询、删边和再次连接的完整生命周期，验证离线区间与回滚并查集。',
     '能把边的有效时间映射到查询区间，并在分治返回时恢复并查集状态。',
     'SAMPLE_EXPLANATION', 'ALGO.GRAPH.CONNECT.并查集', 'SK_UNION_FIND_COMPONENT'),
    ('可撤销道路连通性', 1, 'TCI_ROLLBACK_DSU_DELETION_BOUNDARY', 'STRUCTURAL', '删除后的孤立关系',
     '覆盖删除有效边后重新添加其他边的结构，检查时间区间端点和撤销顺序。',
     '能维护边存在区间的闭开边界，并确认回滚不会残留已离开时间段的连接。',
     'SUBTASK_GATE', 'ALGO.GRAPH.CONNECT.并查集', 'SK_V9_DSU_PARENT_ROOT_CONTRACT'),

    ('最长重复路线片段', 0, 'TCI_REPEAT_SUBSTRING_OVERLAP', 'REPRESENTATIVE', '重叠重复片段',
     '覆盖重复片段允许重叠的字符串，检查长度判定、窗口枚举和边界更新。',
     '能区分重叠出现与不允许重叠的模型，并完整枚举给定长度的所有子串。',
     'SAMPLE_EXPLANATION', 'BASIC.STRING.MATCH.重叠匹配计数', 'SK_V8_STRING_MATCH_SENTINEL_OVERLAP'),
    ('最长重复路线片段', 1, 'TCI_REPEAT_SUBSTRING_NONE', 'BOUNDARY', '无重复片段',
     '覆盖所有字符互异的下界场景，检查未找到重复时的哨兵和答案边界。',
     '能让判定函数在没有合法重复时返回一致结果，并使二分或枚举收束到零。',
     'CORRECTNESS_GUARD', 'BASIC.STRING.MATCH.未找到结果哨兵', 'SK_V8_STRING_MATCH_SENTINEL_OVERLAP'),

    ('仓库滑窗调平代价', 0, 'TCI_WINDOW_MEDIAN_ODD', 'REPRESENTATIVE', '奇数窗口中位数',
     '覆盖奇数长度滑动窗口，验证加入、移除、中位数和代价更新的基本不变量。',
     '能维护窗口元素集合与中位数两侧的平衡，并在每次滑动后更新答案。',
     'SAMPLE_EXPLANATION', 'ALGO.TWO_POINTERS.WINDOW.窗口扩张', 'SK_WINDOW_INVARIANT'),
    ('仓库滑窗调平代价', 1, 'TCI_WINDOW_MEDIAN_DUPLICATE_EVEN', 'EDGE_CASE', '重复值与偶数窗口',
     '覆盖重复值和偶数长度窗口，检查双堆归属、延迟删除与中位数约定。',
     '能在键值重复时仍删除正确实例，并保持堆大小和有效元素计数一致。',
     'SUBTASK_GATE', 'ALGO.GREEDY.STRUCTURE.优先队列辅助', 'SK_V9_PRIORITY_QUEUE_ORDER_AND_LAZY_DELETE'),

    ('分层优惠最短路', 0, 'TCI_LAYERED_SHORTEST_COUPON', 'REPRESENTATIVE', '优惠券状态最短路',
     '覆盖使用与保留优惠券的多层状态竞争，验证按节点和已用资源数共同松弛。',
     '能把资源使用次数纳入距离状态，并比较同一节点不同层的后续价值。',
     'SAMPLE_EXPLANATION', 'ALGO.GRAPH.SHORTEST.Dijkstra', 'SK_V13_SHORTEST_DISTANCE_RELAXATION_CONTRACT'),
    ('分层优惠最短路', 1, 'TCI_LAYERED_SHORTEST_ZERO_COUPON', 'BOUNDARY', '零资源层边界',
     '覆盖不能使用优惠券的边界层，检查状态数组初始化和普通最短路退化行为。',
     '能在资源上限为零时只保留合法状态，并与标准最短路结果保持一致。',
     'CORRECTNESS_GUARD', 'ALGO.GRAPH.SHORTEST.距离初始化', 'SK_V7_GRAPH_RELAXATION_AND_TOPO_CONTRACT'),

    ('双工位装配最短完成时间', 0, 'TCI_ASSEMBLY_SCHEDULE_DEPENDENCY', 'REPRESENTATIVE', '依赖释放与并行调度',
     '覆盖任务依赖和双工位并行执行，检查完成事件、可用任务集合和状态转移。',
     '能用状态表示已完成任务集合，并在同一时刻释放所有新满足依赖的任务。',
     'SAMPLE_EXPLANATION', 'MATH.BIT.MASK.状态判断', 'SK_BITMASK_STATE_MEANING'),
    ('双工位装配最短完成时间', 1, 'TCI_ASSEMBLY_SCHEDULE_NO_DEPENDENCY', 'EDGE_CASE', '无依赖任务边界',
     '覆盖没有依赖边的最小调度结构，检查初始可用集合和双工位并发选择。',
     '能正确构造空依赖下的初始状态，并避免把串行顺序误当作必要约束。',
     'CORRECTNESS_GUARD', 'ALGO.DP.INIT.边界状态', 'SK_V13_DP_STATE_TRANSITION_ORDER_CONTRACT'),

    ('矩形能量场统计', 0, 'TCI_MATRIX_DIFF_OVERLAP', 'REPRESENTATIVE', '二维矩形叠加',
     '覆盖多个闭区间矩形重叠和阈值统计，验证四角差分、还原与计数流程。',
     '能把闭区间更新转换为二维差分事件，并按正确维度还原每个网格值。',
     'SAMPLE_EXPLANATION', 'ALGO.PREFIX.DIFF.区间加', 'SK_V6_PREFIX_DIFF_BOUNDARY_FORMULA'),
    ('矩形能量场统计', 1, 'TCI_MATRIX_DIFF_EDGE_ROW', 'BOUNDARY', '贴边单行矩形',
     '覆盖贴近矩阵边界的单行更新和零阈值，检查补零空间与端点偏移。',
     '能处理坐标位于首尾行列的更新，避免越界并完整统计边界单元格。',
     'CORRECTNESS_GUARD', 'ALGO.PREFIX.MATRIX.边界补零', 'SK_V13_MATRIX_PREFIX_INCLUSION_CONTRACT'),

    ('子数组最小值贡献和', 0, 'TCI_MONOTONIC_STACK_REPRESENTATIVE', 'REPRESENTATIVE', '单调栈贡献划分',
     '覆盖大小关系交错的数组，验证左右边界、贡献区间和累计求和。',
     '能定义栈内单调性，并说明每个元素作为最小值负责的子数组范围。',
     'SAMPLE_EXPLANATION', 'DS.LINEAR.STACK.单调栈雏形', 'SK_V12_STACK_QUEUE_STATE_CONTRACT'),
    ('子数组最小值贡献和', 1, 'TCI_MONOTONIC_STACK_DUPLICATES', 'EDGE_CASE', '重复值归属规则',
     '覆盖相邻元素相等的情况，检查一侧严格、一侧非严格的去重归属。',
     '能为重复最小值规定唯一贡献边界，避免同一子数组被重复计算或遗漏。',
     'SUBTASK_GATE', 'DS.LINEAR.STACK.单调栈雏形', 'SK_V12_STACK_QUEUE_STATE_CONTRACT'),

    ('潮汐折扣最短路', 0, 'TCI_TIDE_DISCOUNT_REPRESENTATIVE', 'REPRESENTATIVE', '时间与资源联合状态',
     '覆盖周期等待、道路方向和一次优惠资源共同作用的公开场景。',
     '能用节点、资源状态和到达时间共同定义距离，并按真实可出发时间松弛。',
     'SAMPLE_EXPLANATION', 'ALGO.GRAPH.SHORTEST.Dijkstra', 'SK_V13_SHORTEST_DISTANCE_RELAXATION_CONTRACT'),
    ('潮汐折扣最短路', 1, 'TCI_TIDE_DISCOUNT_UNREACHABLE', 'STRUCTURAL', '有向不可达边界',
     '覆盖道路方向导致目标不可达且资源上限处在边界的结构。',
     '能保持有向建边语义，正确识别所有合法状态都无法到达终点的情况。',
     'CORRECTNESS_GUARD', 'ALGO.GRAPH.SHORTEST.距离初始化', 'SK_V7_GRAPH_RELAXATION_AND_TOPO_CONTRACT'),
    ('潮汐折扣最短路', 2, 'TCI_TIDE_DISCOUNT_STATE_DOMINANCE', 'STATE_SPACE', '资源保留状态支配',
     '覆盖较早到达与保留优惠资源之间不能只按单一时间剪枝的状态竞争。',
     '能识别同一节点的不同资源余量属于不同状态，并只在相同状态维度比较优劣。',
     'SUBTASK_GATE', 'ALGO.SEARCH.STATE.状态编码', 'SK_V7_GRAPH_SEARCH_LAYER_VISITED_CONTRACT');

DO $$
DECLARE
    managed_problem_count integer;
    managed_case_count integer;
    matched_case_count integer;
    invalid_mapping_count integer;
BEGIN
    SELECT count(*) INTO managed_problem_count
    FROM public.problems p
    WHERE p.title IN (SELECT DISTINCT problem_title FROM test_case_semantic_v1);

    IF managed_problem_count > 0 AND managed_problem_count <> 16 THEN
        RAISE EXCEPTION 'V8 found a partial managed problem cohort: % of 16', managed_problem_count;
    END IF;

    SELECT count(*) INTO managed_case_count
    FROM public.test_cases tc
    JOIN public.problems p ON p.id = tc.problem_id
    WHERE p.title IN (SELECT DISTINCT problem_title FROM test_case_semantic_v1);

    IF managed_problem_count = 16 AND managed_case_count <> 45 THEN
        RAISE EXCEPTION 'V8 expected exactly 45 managed test cases, found %', managed_case_count;
    END IF;

    SELECT count(*) INTO matched_case_count
    FROM test_case_semantic_v1 v
    JOIN public.problems p ON p.title = v.problem_title
    JOIN public.test_cases tc ON tc.problem_id = p.id AND tc.order_index = v.order_index;

    IF managed_problem_count = 16 AND matched_case_count <> 45 THEN
        RAISE EXCEPTION 'V8 expected exactly 45 semantic mappings, found %', matched_case_count;
    END IF;

    SELECT count(*) INTO invalid_mapping_count
    FROM test_case_semantic_v1 v
    LEFT JOIN public.informatics_knowledge_nodes k
        ON k.code = v.knowledge_node_code AND k.enabled = true
    LEFT JOIN public.ai_standard_skill_units s
        ON s.code = v.skill_unit_code AND s.enabled = true
    WHERE k.code IS NULL
       OR s.code IS NULL
       OR NOT (
            s.primary_knowledge_node_code = v.knowledge_node_code
            OR v.knowledge_node_code = ANY (
                regexp_split_to_array(replace(coalesce(s.knowledge_node_codes, ''), E'\r', ''), E'\n+')
            )
       );

    IF managed_problem_count = 16 AND invalid_mapping_count <> 0 THEN
        RAISE EXCEPTION 'V8 contains % invalid knowledge/skill mappings', invalid_mapping_count;
    END IF;
END $$;

UPDATE public.test_cases tc
SET semantic_code = v.semantic_code,
    intent_type = v.intent_type,
    intent_title = v.intent_title,
    intent_summary = v.intent_summary,
    learning_objective = v.learning_objective,
    contest_role = v.contest_role,
    reveal_policy = CASE WHEN tc.is_hidden THEN 'AI_GENERALIZED' ELSE 'PUBLIC_EXAMPLE' END,
    knowledge_node_code = v.knowledge_node_code,
    skill_unit_code = v.skill_unit_code,
    review_status = 'REVIEWED',
    source_reference = 'ACM_IEEE_CS2023_TESTING;ICPC_PPF_2023_07;EEF_FEEDBACK_2021;PROJECT_CASE_REVIEW_2026_07_20',
    library_version = 'test-case-semantic-quality-v1',
    reviewed_at = timestamp '2026-07-20 00:00:00'
FROM test_case_semantic_v1 v
JOIN public.problems p ON p.title = v.problem_title
WHERE tc.problem_id = p.id
  AND tc.order_index = v.order_index;

ALTER TABLE public.submission_case_results
    ADD COLUMN IF NOT EXISTS test_case_id bigint,
    ADD COLUMN IF NOT EXISTS test_semantic_code varchar(160),
    ADD COLUMN IF NOT EXISTS test_intent_type varchar(40),
    ADD COLUMN IF NOT EXISTS test_intent_title varchar(160),
    ADD COLUMN IF NOT EXISTS test_intent_summary varchar(800),
    ADD COLUMN IF NOT EXISTS test_learning_objective varchar(800),
    ADD COLUMN IF NOT EXISTS test_contest_role varchar(40),
    ADD COLUMN IF NOT EXISTS test_reveal_policy varchar(40);

CREATE INDEX IF NOT EXISTS idx_submission_case_results_test_case
    ON public.submission_case_results (test_case_id);

ALTER TABLE public.submission_case_results
    DROP CONSTRAINT IF EXISTS fk_submission_case_result_test_case,
    ADD CONSTRAINT fk_submission_case_result_test_case
        FOREIGN KEY (test_case_id)
        REFERENCES public.test_cases(id)
        ON DELETE SET NULL,
    DROP CONSTRAINT IF EXISTS ck_submission_case_semantic_snapshot,
    ADD CONSTRAINT ck_submission_case_semantic_snapshot CHECK (
        (
            test_semantic_code IS NULL
            AND test_intent_type IS NULL
            AND test_intent_title IS NULL
            AND test_intent_summary IS NULL
            AND test_learning_objective IS NULL
            AND test_contest_role IS NULL
            AND test_reveal_policy IS NULL
        )
        OR
        (
            test_case_id IS NOT NULL
            AND test_semantic_code IS NOT NULL
            AND test_intent_type IS NOT NULL
            AND test_intent_title IS NOT NULL
            AND test_intent_summary IS NOT NULL
            AND test_learning_objective IS NOT NULL
            AND test_contest_role IS NOT NULL
            AND test_reveal_policy IS NOT NULL
        )
    );

WITH ordered_test_cases AS (
    SELECT
        tc.id AS test_case_id,
        tc.problem_id,
        row_number() OVER (
            PARTITION BY tc.problem_id
            ORDER BY tc.order_index, tc.id
        ) AS test_case_number
    FROM public.test_cases tc
),
resolved AS (
    SELECT
        scr.id AS case_result_id,
        otc.test_case_id
    FROM public.submission_case_results scr
    JOIN public.submissions s ON s.id = scr.submission_id
    JOIN ordered_test_cases otc
      ON otc.problem_id = s.problem_id
     AND otc.test_case_number = scr.test_case_number
)
UPDATE public.submission_case_results scr
SET test_case_id = resolved.test_case_id
FROM resolved
WHERE scr.id = resolved.case_result_id
  AND scr.test_case_id IS NULL;

UPDATE public.submission_case_results scr
SET test_semantic_code = tc.semantic_code,
    test_intent_type = tc.intent_type,
    test_intent_title = tc.intent_title,
    test_intent_summary = tc.intent_summary,
    test_learning_objective = tc.learning_objective,
    test_contest_role = tc.contest_role,
    test_reveal_policy = tc.reveal_policy
FROM public.test_cases tc
WHERE scr.test_case_id = tc.id
  AND scr.test_semantic_code IS NULL
  AND tc.semantic_code IS NOT NULL;

DO $$
DECLARE
    managed_case_count integer;
    semantic_case_count integer;
    distinct_intent_problem_count integer;
    invalid_snapshot_count integer;
BEGIN
    SELECT count(*) INTO managed_case_count
    FROM public.test_cases tc
    JOIN public.problems p ON p.id = tc.problem_id
    WHERE p.title IN (SELECT DISTINCT problem_title FROM test_case_semantic_v1);

    SELECT count(*) INTO semantic_case_count
    FROM public.test_cases tc
    JOIN public.problems p ON p.id = tc.problem_id
    WHERE p.title IN (SELECT DISTINCT problem_title FROM test_case_semantic_v1)
      AND tc.semantic_code IS NOT NULL
      AND tc.review_status = 'REVIEWED'
      AND tc.library_version = 'test-case-semantic-quality-v1';

    IF managed_case_count > 0 AND semantic_case_count <> 45 THEN
        RAISE EXCEPTION 'V8 expected 45 reviewed semantic test cases, found %', semantic_case_count;
    END IF;

    SELECT count(*) INTO distinct_intent_problem_count
    FROM (
        SELECT tc.problem_id
        FROM public.test_cases tc
        JOIN public.problems p ON p.id = tc.problem_id
        WHERE p.title IN (SELECT DISTINCT problem_title FROM test_case_semantic_v1)
        GROUP BY tc.problem_id
        HAVING count(DISTINCT tc.intent_type) >= 2
    ) covered;

    IF managed_case_count > 0 AND distinct_intent_problem_count <> 16 THEN
        RAISE EXCEPTION 'V8 expected all 16 problems to have at least two intent types, found %', distinct_intent_problem_count;
    END IF;

    SELECT count(*) INTO invalid_snapshot_count
    FROM public.submission_case_results scr
    JOIN public.test_cases tc ON tc.id = scr.test_case_id
    WHERE tc.semantic_code IS NOT NULL
      AND (
          scr.test_semantic_code IS NULL
          OR scr.test_intent_type IS NULL
          OR scr.test_intent_summary IS NULL
          OR scr.test_reveal_policy IS NULL
      );

    IF invalid_snapshot_count <> 0 THEN
        RAISE EXCEPTION 'V8 left % mapped historical case results without semantic snapshots', invalid_snapshot_count;
    END IF;
END $$;

DROP TABLE test_case_semantic_v1;
