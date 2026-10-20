-- 标准库与知识库存量治理：补实旧修正策略、消歧同名知识、拉开课堂与竞赛检查动作。
-- 稳定 code、知识层级和历史业务事实保持不变；规范表仍是权威来源，平铺表只同步兼容快照。

DROP TABLE IF EXISTS standard_library_v12_business_counts;
CREATE TEMP TABLE standard_library_v12_business_counts AS
SELECT
    (SELECT count(*) FROM public.problems) AS problems,
    (SELECT count(*) FROM public.test_cases) AS test_cases,
    (SELECT count(*) FROM public.submissions) AS submissions,
    (SELECT count(*) FROM public.submission_analyses) AS submission_analyses,
    (SELECT count(*) FROM public.submission_diagnosis_facts) AS diagnosis_facts,
    (SELECT count(*) FROM public.student_ai_feedbacks) AS student_feedbacks,
    (SELECT count(*) FROM public.student_recommendation_events) AS recommendation_events;

DROP TABLE IF EXISTS standard_library_v12_baseline;
CREATE TEMP TABLE standard_library_v12_baseline AS
SELECT
    (SELECT count(*)
     FROM public.informatics_knowledge_nodes
     WHERE code IN (
         'BASIC.FUNCTION.DEF.函数命名', 'ENG.STYLE.NAME.函数命名',
         'ALGO.SEARCH.DFS.回溯恢复', 'BASIC.RECURSION.STATE.回溯恢复',
         'CONTEST.PATTERN.STRUCTURE.图结构', 'CONTEST.READING.INPUT.图结构', 'DS.GRAPH',
         'ALGO.PREFIX.MATRIX.坐标偏移', 'MATH.GEOMETRY.COORD.坐标偏移',
         'ALGO.ENUM.COMPLEXITY.数据范围反推', 'CONTEST.PATTERN.RANGE', 'ENG.COMPLEXITY.TIME.数据范围反推',
         'CONTEST.READING.INPUT.数组规模', 'ENG.COMPLEXITY.SPACE.数组规模',
         'ALGO.SEARCH.BFS.最短步数', 'MATH.GEOMETRY.DIST.最短步数',
         'ALGO.SIM.CORNER.样例复现', 'ENG.DEBUG.SAMPLE',
         'ALGO.PREFIX.DIFF.离线处理', 'ENG.COMPLEXITY.TRADEOFF.离线处理',
         'DS.SET_MAP.HASH.空间换时间', 'ENG.COMPLEXITY.TRADEOFF.空间换时间',
         'ALGO.SIM.PROCESS.终止条件', 'ALGO.TWO_POINTERS.OPPOSITE.终止条件', 'BASIC.RECURSION.BASE.终止条件',
         'ALGO.SEARCH.BFS.访问标记', 'DS.GRAPH.TRAVERSE.访问标记',
         'ALGO.SEARCH.DFS.路径记录', 'DS.GRAPH.TRAVERSE.路径记录',
         'ENG.COMPLEXITY.SPACE.递归栈', 'ENG.DEBUG.TRACE.递归栈',
         'BASIC.ARRAY.PREFIX.频次统计', 'DS.SET_MAP.MAP.频次统计'
     )) AS disambiguation_targets,
    (SELECT count(*)
     FROM public.ai_standard_mistake_points
     WHERE enabled = true
       AND repair_strategy = '该易错点用于约束 AI 返回标准化错因 ID 和名称，具体诊断、修正建议和提高建议由 AI 结合题目、代码与判题结果生成。') AS meta_repairs,
    (SELECT count(*)
     FROM public.ai_standard_mistake_points
     WHERE enabled = true
       AND skill_unit_code LIKE 'SK_COMPAT_%'
       AND repair_strategy = '该易错点用于约束 AI 返回标准化错因 ID 和名称，具体诊断、修正建议和提高建议由 AI 结合题目、代码与判题结果生成。') AS compatibility_meta_repairs,
    (SELECT count(*)
     FROM public.ai_standard_mistake_points
     WHERE enabled = true
       AND code IN ('MP_V7_STRING_OVERLAPPING_MATCH_SKIPPED', 'MP_V8_STRING_OVERLAP_MATCH_SKIPPED')) AS overlap_mistakes,
    (SELECT count(*)
     FROM public.ai_standard_application_scenarios
     WHERE enabled = true
       AND context_type = 'CONTEST'
       AND library_version = 'informatics-discipline-application-v2') AS v11_contest_scenarios;

-- 15 组同名节点按路径观察面消歧。原术语进入 aliases，稳定 code、parent_code 与 type 不变。
DROP TABLE IF EXISTS standard_library_v12_knowledge_curations;
CREATE TEMP TABLE standard_library_v12_knowledge_curations (
    code text PRIMARY KEY,
    new_name text NOT NULL,
    description text NOT NULL,
    learning_objectives text NOT NULL,
    typical_problems text NOT NULL,
    aliases text NOT NULL
);

INSERT INTO standard_library_v12_knowledge_curations VALUES
    ('BASIC.FUNCTION.DEF.函数命名', '函数接口命名',
     '函数名应直接表达接口职责和结果语义，使调用处无需展开实现就能判断它读取什么、改变什么、返回什么；布尔函数宜体现判断含义，转换函数宜体现输入到输出的方向。',
     E'能根据函数合同选择动词与对象。\n能从调用处识别名称与副作用不一致的问题。',
     E'判断函数使用含糊名 process\n读取函数名称却隐式修改全局状态\n布尔函数名称与真假含义相反',
     E'函数命名\n函数接口名\nfunction naming\nAPI naming'),
    ('ENG.STYLE.NAME.函数命名', '可读性函数命名',
     '可读性函数命名要求同一项目内使用一致词汇、粒度和命名风格，避免缩写漂移、同义词混用或名称比函数职责更宽，从而让调试轨迹和代码审查能稳定指向同一行为。',
     E'能识别项目内同义命名和粒度失配。\n能通过重命名让调用链表达业务步骤。',
     E'calc、compute、solve 混用\n函数名承诺排序但只做过滤\n辅助函数缩写导致调试记录难检索',
     E'函数命名\n可读性命名\nreadable function names\ncode naming'),
    ('ALGO.SEARCH.DFS.回溯恢复', 'DFS 选择撤销',
     'DFS 回溯必须形成 choose—recurse—unchoose 对称结构：本层加入路径、标记或资源后，子调用返回时按相反顺序撤销，保证兄弟分支从相同父状态开始。',
     E'能列出一次 DFS 选择修改的全部共享状态。\n能检查正常返回、剪枝和提前返回是否都完成撤销。',
     E'排列枚举路径未 pop\n访问标记跨兄弟分支残留\n剪枝 return 跳过资源恢复',
     E'回溯恢复\n撤销选择\nDFS backtracking\nchoose recurse unchoose'),
    ('BASIC.RECURSION.STATE.回溯恢复', '递归共享状态恢复',
     '递归共享状态恢复关注调用帧之外的可变对象；每层进入前保存本层会覆盖的值，返回后恢复到进入该层时的快照，局部变量则由调用栈自然隔离。',
     E'能区分局部状态与跨层共享状态。\n能用进入/退出快照验证恢复是否完整。',
     E'递归修改全局计数后未减回\n数组原位置被覆盖且未保存旧值\n多个递归分支共用容器导致串支',
     E'回溯恢复\n递归状态恢复\nrecursive state restoration\nshared state rollback'),
    ('CONTEST.PATTERN.STRUCTURE.图结构', '图型题结构识别',
     '图型题结构识别从对象间的二元关系、可达性、依赖、连接代价或状态转移中判断是否应建图，并区分这是显式图、隐式状态图还是树形特例。',
     E'能从题意关系抽取点和边。\n能区分图问题与仅使用邻接存储的非图问题。',
     E'关系网络的连通性\n操作状态的最少步数\n任务依赖的可行顺序',
     E'图结构\n图型题\ngraph pattern recognition\ngraph problem'),
    ('CONTEST.READING.INPUT.图结构', '图输入合同识别',
     '图输入合同必须明确节点数、边数、方向、权值、编号范围以及是否允许重边、自环和不连通；边记录未出现某个合法节点时，该节点仍属于图。',
     E'能把每个输入字段映射到点、边和属性。\n能在读入前写出方向、编号和异常结构合同。',
     E'n m 后跟边记录\n孤立节点未出现在边中\n无向边只加入一个方向',
     E'图结构\n图输入结构\ngraph input contract\nedge list parsing'),
    ('DS.GRAPH', '图结构',
     '图结构由节点、边及其方向和属性组成，用于表达对象间关系；学习时应同时掌握邻接表示、连通与可达语义，以及遍历过程中访问状态的生命周期。',
     E'能说明点、边、方向、权值和邻接关系。\n能依据稠密度与操作选择邻接表、邻接矩阵或边集。',
     E'社交关系图\n道路网络\n依赖图与状态图',
     E'图数据结构\ngraph\ngraph data structure'),
    ('ALGO.PREFIX.MATRIX.坐标偏移', '二维前缀坐标补位',
     '二维前缀坐标补位通常用额外第 0 行和第 0 列把原矩阵 (r,c) 映射到前缀表 (r+1,c+1)，从而让四角容斥公式在首行首列不需要分支特判。',
     E'能写出原矩阵坐标与前缀表坐标的双向映射。\n能用单行、单列和左上角查询检查补位。',
     E'二维前缀少加一位\n查询端点少减一\n首行首列容斥越界',
     E'坐标偏移\n二维前缀补位\nprefix matrix padding\ncoordinate shift'),
    ('MATH.GEOMETRY.COORD.坐标偏移', '坐标平移映射',
     '坐标平移映射给每一维加固定偏移，把负坐标或非零起点映射为可存储下标；统一平移保持点间差值、顺序和距离不变，输出原坐标时必须执行逆映射。',
     E'能根据最小与最大坐标选择偏移和数组长度。\n能验证正向映射与逆映射互为逆操作。',
     E'负坐标映射到数组\n偏移后数组长度不足\n输出忘记减回 offset',
     E'坐标偏移\n坐标平移\ncoordinate translation\noffset mapping'),
    ('ALGO.ENUM.COMPLEXITY.数据范围反推', '枚举范围与复杂度反推',
     '枚举范围与复杂度反推把候选维度、循环层数、分支数和单次检查成本共同代入约束，判断暴力、剪枝或预处理后的枚举是否能在时间预算内完成。',
     E'能从 n 和候选维度估算枚举次数。\n能定位复杂度中被忽略的检查或分支成本。',
     E'二重枚举加线性检查\n子集枚举 2^n\n枚举加剪枝的上界估算',
     E'数据范围反推\n枚举复杂度\nenumeration budget\nconstraint analysis'),
    ('CONTEST.PATTERN.RANGE', '数据范围驱动的题型识别',
     '数据范围驱动的题型识别把约束作为候选算法族的过滤器：小规模允许穷举或状态压缩，中等规模常需排序、前缀或平方级优化，大规模通常要求线性或 n log n。',
     E'能用数量级排除明显不可行方案。\n能结合数据结构与查询次数缩小算法候选。',
     E'n≤20 的子集问题\nn≤2000 的平方算法\nn,q≤2×10^5 的查询题',
     E'数据范围反推\n约束识别题型\nconstraint-driven pattern\nalgorithm family'),
    ('ENG.COMPLEXITY.TIME.数据范围反推', '约束规模与复杂度预算',
     '约束规模与复杂度预算把输入上限、测试组数、查询次数、常数开销和语言运行环境换算为操作数量级，并用最大规模压力测试验证实现而非只验证算法名称。',
     E'能估算主循环、排序和容器操作的总成本。\n能区分理论复杂度与实现中的隐藏重复工作。',
     E'多组数据总规模\n循环内重复排序\n字符串构造隐藏在线性操作中',
     E'数据范围反推\n复杂度预算\ntime budget\nconstraint-driven complexity'),
    ('CONTEST.READING.INPUT.数组规模', '输入规模与数组边界',
     '输入规模与数组边界要求先识别 n 是元素个数、最大编号还是行列之一，再据此确定分配长度、合法下标和多组数据的重置范围。',
     E'能把输入规模映射到下标区间和分配长度。\n能识别 0 基、1 基与哨兵额外空间。',
     E'n 个元素却访问 a[n]\n1 基数组只分配 n\n多组数据只清空新 n 范围',
     E'数组规模\n输入数组长度\narray input size\nindex range'),
    ('ENG.COMPLEXITY.SPACE.数组规模', '数组内存规模估算',
     '数组内存规模由元素总数、单元素字节数、数组份数、容器额外开销和分配位置共同决定；二维展开、复制和栈上大数组都必须计入峰值。',
     E'能计算一维、二维和多份缓冲的峰值字节数。\n能根据内存上限选择类型、布局和分配位置。',
     E'二维 long long 矩阵\n双缓冲数组\n局部大数组造成栈溢出',
     E'数组规模\n数组内存估算\narray memory footprint\nspace complexity'),
    ('ALGO.SEARCH.BFS.最短步数', 'BFS 最短步数',
     '在每条转移代价相同的状态图中，BFS 按距离分层扩展；节点第一次入队时得到从起点到它的最短步数，访问标记应与入队同时建立。',
     E'能把操作定义为等权状态边。\n能用层次和首次发现证明最短步数。',
     E'网格最短步数\n整数状态变换\n多源 BFS 距离',
     E'最短步数\nBFS shortest steps\nunweighted shortest path'),
    ('MATH.GEOMETRY.DIST.最短步数', '几何最少移动步数',
     '几何最少移动步数先由允许的移动向量、每步代价和障碍条件定义距离模型；只有可交换且无障碍的规则才能直接化为坐标差公式，否则需要状态搜索。',
     E'能从移动规则推导曼哈顿、切比雪夫或其他步数公式。\n能识别公式失效并转为搜索的条件。',
     E'四方向移动\n八方向移动\n带障碍或不同代价的坐标移动',
     E'最短步数\n几何移动距离\nminimum moves\ngrid metric'),
    ('ALGO.SIM.CORNER.样例复现', '模拟流程的样例复现',
     '模拟流程的样例复现把样例拆成按时间或输入顺序发生的事件，逐步记录关键状态、分支和输出时机，用来检查实现是否忠实执行题面流程。',
     E'能为每一步事件建立状态快照。\n能定位样例输出与程序输出首次分叉的事件。',
     E'事件模拟状态表\n同一时刻更新顺序\n最后事件结算遗漏',
     E'样例复现\n模拟样例轨迹\nsimulation trace\nsample replay'),
    ('ENG.DEBUG.SAMPLE', '样例复现与首个偏差',
     '样例复现与首个偏差要求同时手算预期轨迹和记录程序实际轨迹，比较两者第一次不同的变量或控制流；后续错误通常只是首个偏差的传播结果。',
     E'能选择最小样例和关键观察点。\n能在首个偏差处提出可证伪的修复假设。',
     E'只比较最终输出\n日志过多淹没首个异常\n修复后只重跑原样例',
     E'样例复现\n首个偏差\nfirst divergence\ndebug trace'),
    ('ALGO.PREFIX.DIFF.离线处理', '差分更新的离线处理',
     '差分更新的离线处理先把全部区间修改记在边界变化上，所有修改收集完后再做一次前缀恢复；在恢复前读取最终数组会得到未物化状态。',
     E'能把区间更新写成两个差分端点。\n能区分记录更新阶段与恢复结果阶段。',
     E'多次区间加\n二维差分恢复\n边改边查询导致状态混用',
     E'离线处理\n差分离线更新\noffline difference\nbatch range update'),
    ('ENG.COMPLEXITY.TRADEOFF.离线处理', '离线处理的时空权衡',
     '离线处理允许收集并重排全部请求，用额外存储和预处理减少重复计算；采用前必须确认答案不依赖实时顺序，并保留原请求编号恢复输出顺序。',
     E'能判断任务是否允许重排请求。\n能计算预处理、存储和单次查询的总成本。',
     E'离线排序查询\n坐标压缩后批处理\n输出顺序恢复错误',
     E'离线处理\n批处理权衡\noffline processing\ntime-space tradeoff'),
    ('DS.SET_MAP.HASH.空间换时间', '哈希索引的空间换时间',
     '哈希索引用额外桶和键值存储把重复查找从扫描改为期望常数时间；收益依赖键的等价规则、哈希质量、负载因子和是否需要顺序信息。',
     E'能说明 key、value 与被替代扫描的关系。\n能识别哈希不适用的顺序或内存约束。',
     E'两数和索引\n去重与频次统计\n复合 key 的哈希设计',
     E'空间换时间\n哈希索引\nhash lookup\nspace for time'),
    ('ENG.COMPLEXITY.TRADEOFF.空间换时间', '空间换时间的成本评估',
     '空间换时间的成本评估比较基线重复计算与缓存、预计算或索引方案的构建时间、峰值内存、命中率和失效成本，不能只看到查询变快而忽略初始化与复制。',
     E'能画出预处理、查询和更新三阶段成本。\n能根据内存上限与查询次数判断权衡是否成立。',
     E'前缀表预计算\n记忆化缓存\n多份索引造成内存超限',
     E'空间换时间\n时空权衡\ntime-space tradeoff\nprecomputation cost'),
    ('ALGO.SIM.PROCESS.终止条件', '模拟流程终止条件',
     '模拟流程终止条件由题面事件耗尽、目标状态达成或无合法动作等业务条件决定；应在正确的状态更新时间检查，避免少处理最后事件或多执行一步。',
     E'能指出终止判断发生在事件前还是事件后。\n能覆盖零事件、最后一步达成和永不达成情况。',
     E'while 多执行一次\n最后事件未结算\n使用合法状态值作为结束哨兵',
     E'终止条件\n模拟结束条件\nsimulation termination\nstop condition'),
    ('ALGO.TWO_POINTERS.OPPOSITE.终止条件', '对向指针终止条件',
     '对向双指针的终止条件由候选区间含义决定：若 left 和 right 都是有效候选，通常在 left>right 时结束；若只处理成对对象，则可能在 left>=right 时结束。',
     E'能从候选集合推导 <、<= 与结束状态。\n能用空、单元素和双元素输入验证。',
     E'单元素候选漏检\n指针相遇后重复计数\n更新不推进导致死循环',
     E'终止条件\n双指针结束条件\ntwo pointers termination\ncandidate interval'),
    ('BASIC.RECURSION.BASE.终止条件', '递归终止条件',
     '递归终止条件必须覆盖最小合法子问题，并与每次调用的严格推进共同保证有限结束；基础返回值还要是后续合并运算的正确单位或边界结果。',
     E'能写出规模度量和每次递归如何变小。\n能验证最小输入的返回值与合并公式一致。',
     E'缺少空结构基础情况\n参数不变导致无限递归\n基础返回值差一',
     E'终止条件\n递归出口\nrecursion base case\nprogress measure'),
    ('ALGO.SEARCH.BFS.访问标记', 'BFS 入队访问标记',
     'BFS 应在节点第一次入队时立即建立访问标记或距离，保证同一节点只进入队列一次；若等到出队才标记，环和重边会制造大量重复状态。',
     E'能解释发现、入队、出队三个时刻的状态。\n能用环和重边统计每个节点入队次数。',
     E'出队后才 visited\n多源起点重复入队\n距离被后续更长路径覆盖',
     E'访问标记\nBFS visited\nmark on enqueue\nfirst discovery'),
    ('DS.GRAPH.TRAVERSE.访问标记', '图遍历访问状态',
     '图遍历访问状态区分未发现、已发现待处理和已完成等生命周期；具体使用布尔标记、颜色或时间戳取决于是否需要检测环、处理多轮遍历或恢复父子关系。',
     E'能选择与遍历目标匹配的状态集合。\n能说明每种状态在何时写入、何时读取。',
     E'DFS 三色判环\n多轮搜索时间戳\n连通分量标号',
     E'访问标记\n图遍历状态\nvisited state\ngraph traversal color'),
    ('ALGO.SEARCH.DFS.路径记录', 'DFS 当前路径记录',
     'DFS 当前路径只保存从搜索根到当前递归层的选择序列；进入分支时追加，返回父层时删除，记录答案时应复制快照而不是保存会继续变化的共享引用。',
     E'能区分当前路径、已完成答案和父节点数组。\n能验证路径长度与递归深度同步。',
     E'路径未 pop 导致串支\n答案保存共享引用\n提前返回留下多余节点',
     E'路径记录\nDFS path\ncurrent recursion path\npath snapshot'),
    ('DS.GRAPH.TRAVERSE.路径记录', '图遍历来源与路径恢复',
     '图遍历路径恢复通常为每个首次发现的节点保存 predecessor 或 parent；找到目标后从终点沿来源链回溯到起点，再反转得到完整路径。',
     E'能在首次发现时记录唯一来源。\n能区分距离、访问状态和路径父节点。',
     E'BFS 最短路父节点\n不可达节点路径恢复\n多源搜索来源标识',
     E'路径记录\n前驱恢复路径\npredecessor path\npath reconstruction'),
    ('ENG.COMPLEXITY.SPACE.递归栈', '递归栈空间估算',
     '递归栈峰值由最大调用深度乘以单层参数、局部变量和返回信息近似决定；分支数量影响总调用次数，但不直接等于同时存在的栈深。',
     E'能从递归结构求最大深度而非总节点数。\n能识别大局部数组和深递归的栈风险。',
     E'线性递归深度 n\n平衡树递归深度 log n\n每层分配大数组',
     E'递归栈\n栈空间\nrecursion stack space\ncall depth'),
    ('ENG.DEBUG.TRACE.递归栈', '递归调用栈追踪',
     '递归调用栈追踪为每一层记录参数、局部状态、子调用返回值和返回位置，并按进入与退出顺序缩进展示，从而定位第一层错误参数或错误合并。',
     E'能画出实际调用树中的活动调用链。\n能区分进入值、返回值和父层合并结果。',
     E'递归参数未推进\n子调用正确但父层合并错误\n共享累计量跨层污染',
     E'递归栈\n调用栈跟踪\ncall frame trace\nrecursion debug'),
    ('BASIC.ARRAY.PREFIX.频次统计', '定值域数组频次统计',
     '定值域数组频次统计用下标表示已知且紧凑的类别，用数组值累计出现次数；必须先确认最小值、最大值、偏移、初始化和不存在类别的默认 0。',
     E'能把值域映射为合法下标。\n能验证所有频次之和等于已处理元素数。',
     E'小写字母计数\n分数桶统计\n负值偏移后的频次数组',
     E'频次统计\n计数数组\ndense frequency array\nhistogram'),
    ('DS.SET_MAP.MAP.频次统计', '键值映射频次统计',
     '键值映射频次统计适合稀疏、动态或不可直接作为数组下标的对象；key 表示被分类对象，value 表示次数，读取不存在键时要区分返回 0 与实际插入新键。',
     E'能设计保持题意等价的 key。\n能检查首次出现、重复累计和不存在键查询。',
     E'单词频次\n坐标对计数\n复合状态出现次数',
     E'频次统计\n映射计数\nfrequency map\ncounting dictionary');

UPDATE public.informatics_knowledge_nodes n
SET name = c.new_name,
    path = regexp_replace(n.path, ' / [^/]+$', ' / ' || c.new_name),
    description = c.description,
    learning_objectives = c.learning_objectives,
    typical_problems = c.typical_problems,
    aliases = c.aliases,
    updated_at = CURRENT_TIMESTAMP
FROM standard_library_v12_knowledge_curations c
WHERE n.code = c.code;

-- 将 348 个正式旧错因按错误类型补成可执行修正协议；10 个兼容标签只保留路由边界。
DROP TABLE IF EXISTS standard_library_v12_repair_targets;
CREATE TEMP TABLE standard_library_v12_repair_targets AS
SELECT m.code,
       CASE
           WHEN m.skill_unit_code LIKE 'SK_COMPAT_%' THEN
               '该条目只保留旧版 code 兼容，不作为正式教学错因。收到此 ID 时，先依据当前代码、判题事实和知识路径映射到具体启用错因；无法映射则保留 PARTIAL 或证据不足，不生成确定性教学结论。'
           ELSE '针对“' || m.name || '”：' ||
               CASE m.mistake_type
                   WHEN 'BOUNDARY' THEN '先写出合法下标、端点和空/单元素合同，用能触发“' || m.symptom || '”的最小边界输入逐步复现；修正比较符、循环范围或返回位置后，覆盖下界、上界、相等、空集和单元素复测。'
                   WHEN 'CE' THEN '从编译器第一条错误开始定位声明、类型和语法结构，不追修后续级联报错；构造最小可编译片段修正后，以开启警告的完整构建确认同类错误和隐式转换都已消失。'
                   WHEN 'COMPLEXITY' THEN '把最大 n、查询数、状态数和单次操作成本写成总操作量，定位造成“' || m.symptom || '”的重复循环、排序或容器调用；替换后用最大规模压力数据记录时间与内存，并与目标复杂度核对。'
                   WHEN 'CONCEPT' THEN '先用一句可检验定义重写相关概念，再构造一个满足定义和一个违反定义的最小例子；让代码判断与定义逐项对应，修正后用正例、反例和临界例证明没有继续依赖错误直觉。'
                   WHEN 'DEBUGGING' THEN '固定能复现“' || m.symptom || '”的最小输入，同时记录手算轨迹与程序轨迹；只追到第一个状态或分支偏差，围绕一个假设修改，再用原反例、相邻边界和无关正常例回归。'
                   WHEN 'INITIALIZATION' THEN '列出每个变量或容器的所有者、初值和重置层级，找到“' || m.symptom || '”发生前遗留或缺失的状态；把初始化移到正确生命周期入口，并用连续两组、空组和首元素输入验证无残留。'
                   WHEN 'INVARIANT' THEN '把循环或数据结构在每一步前后必须成立的关系写成断言，使用最小输入定位“' || m.symptom || '”首次破坏不变量的操作；修正更新顺序后逐步检查初始化、保持和终止三段。'
                   WHEN 'IO_FORMAT' THEN '把输入单位、字段顺序、类型、分隔符和输出格式写成合同，用原始字符可见的最小数据复现“' || m.symptom || '”；修正读写边界后覆盖空白、末尾换行、多组与最大值，并逐字节比较输出。'
                   WHEN 'LOGIC' THEN '把条件拆成真值表或互斥完备的情况表，用最小反例复现“' || m.symptom || '”；修正条件、分支归属或布尔组合后，逐格验证每类输入只进入预期路径且结果只被写入一次。'
                   WHEN 'MODELING' THEN '先明确对象、状态、关系、目标和约束，再把最小样例手工建模并对照“' || m.symptom || '”出现在哪个映射步骤；修正模型后用结构不同的反例验证点、边、状态或计数对象仍与题意一一对应。'
                   WHEN 'ORDER' THEN '列出操作间的读写依赖和必须先后关系，用逐步轨迹找到“' || m.symptom || '”首次出现的顺序；调整排序键、遍历方向或更新时机后，以反序、并列和最小长度输入验证依赖未被破坏。'
                   WHEN 'RUNTIME' THEN '固定最小崩溃或异常输入，记录异常位置前最后一个合法状态，逐项检查越界、空容器、除零、无效引用和递归深度；修正后开启可用的运行时检查，并覆盖相邻边界与最大规模。'
                   WHEN 'STATE' THEN '为状态写出含义、所有者、生命周期和合法取值，逐步快照定位“' || m.symptom || '”首次污染的位置；修正初始化、转移或恢复后，用多组、重复访问和分支交错输入验证状态不串联。'
                   WHEN 'SYNTAX' THEN '从编译器第一条语法错误向外匹配括号、分隔符和语句边界，缩减为最小失败片段；修正结构后重新格式化并完整编译，确认不是通过删代码或注释错误路径掩盖问题。'
                   WHEN 'TRANSITION' THEN '把每个新状态的来源、条件和读写版本写成转移表，用小规模手算定位“' || m.symptom || '”的首个错误转移；修正公式或遍历方向后，对照完整表验证初值、转移和答案位置。'
                   WHEN 'TYPE' THEN '计算输入、乘法、累计和相减等每个中间量的理论范围，定位“' || m.symptom || '”发生前第一次窄化或溢出；在运算开始前统一提升类型，并用正负极值和格式化输出复测。'
                   WHEN 'VALIDATION' THEN '建立独立于正式实现的手算、朴素算法或检查器，先用它稳定复现“' || m.symptom || '”；修正后运行随机小数据对拍、最小反例和最大约束，记录首个分叉而不是只看样例通过。'
                   WHEN 'VALUE_RANGE' THEN '把输入、索引、累计值和中间表达式的最小最大范围逐项列出，用极值复现“' || m.symptom || '”；修正类型、分配长度或范围检查后覆盖最小值、最大值、越界外一位和符号变化。'
                   ELSE '固定能复现“' || m.symptom || '”的最小输入，记录预期与实际的首个偏差；写清修复前提、只修改导致偏差的步骤，再用原反例、相邻边界和正常样例验证。'
               END
       END AS repair_strategy
FROM public.ai_standard_mistake_points m
WHERE m.enabled = true
  AND m.repair_strategy = '该易错点用于约束 AI 返回标准化错因 ID 和名称，具体诊断、修正建议和提高建议由 AI 结合题目、代码与判题结果生成。';

UPDATE public.ai_standard_mistake_points m
SET repair_strategy = r.repair_strategy,
    updated_at = CURRENT_TIMESTAMP
FROM standard_library_v12_repair_targets r
WHERE m.code = r.code;

-- 两个稳定 code 分别表达“题意合同未确认”和“实现推进过远”，避免展示同名但不改历史引用。
UPDATE public.ai_standard_mistake_points
SET name = '未确认重叠语义就按模式长度推进',
    description = '没有先确认题目是否允许重叠出现，就在每次匹配后跳过整个模式串长度，使题意合同在实现前已经被错误收窄。',
    symptom = '题目允许重叠出现，但代码默认只统计互不重叠的出现位置；含共享字符的相邻匹配被整体跳过。',
    misconception = '把替换、分段或不重叠匹配的经验直接套到出现次数统计，没有先从题面确定重叠语义。',
    repair_strategy = '先用模式 aaa、文本 aaaa 之类能区分重叠与不重叠的最小例写出预期起点集合；确认题意允许重叠后，让下一候选起点至少只前进一位或使用能保留前后缀的匹配状态，再用重叠、相邻不重叠和完全不匹配三类数据复测。',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'MP_V7_STRING_OVERLAPPING_MATCH_SKIPPED';

UPDATE public.ai_standard_mistake_points
SET name = '匹配后按完整模式长度推进导致漏计重叠',
    description = '重叠语义已经明确，但实现命中后仍把扫描位置增加模式串长度，导致共享字符后的合法起点没有进入下一轮匹配。',
    symptom = '在允许重叠的计数任务中，第一次命中后指针跨过共享前后缀，程序计数小于逐起点检查或标准匹配算法。',
    misconception = '认为一次完整命中后，模式串覆盖范围内不可能再开始新的合法匹配。',
    repair_strategy = '固定一个存在真前后缀的模式串，逐起点记录匹配结果和命中后的下一位置；把推进规则改为检查下一个起点或按失败函数回退到最长可复用前后缀，随后与朴素逐起点算法随机对拍并覆盖首尾命中。',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'MP_V8_STRING_OVERLAP_MATCH_SKIPPED';

-- 规范表更新后再同步既有平铺兼容快照；平铺表不反向生成正式内容。
UPDATE public.ai_standard_library_items item
SET name = m.name,
    description = m.description,
    common_misconception = m.misconception,
    hintl1 = CASE
        WHEN m.skill_unit_code LIKE 'SK_COMPAT_%'
            THEN '这是旧版兼容标签，先映射到有当前证据支持的具体正式错因。'
        ELSE left('先固定能复现“' || m.name || '”的最小输入，并记录预期与实际的首个偏差。', 800)
    END,
    hintl2 = left('当前需要核验的症状：' || m.symptom, 800),
    hintl3 = left(m.repair_strategy, 800),
    evidence_signals = m.symptom,
    required_evidence = CASE
        WHEN m.skill_unit_code LIKE 'SK_COMPAT_%'
            THEN '旧 ID 本身不是证据；必须映射到当前代码、判题或状态轨迹支持的具体正式错因。'
        ELSE '当前代码、判题结果、最小反例或状态轨迹中至少一项能直接支持该症状。'
    END,
    student_explanation = m.description,
    teacher_explanation = left(m.repair_strategy, 1200),
    teaching_action = CASE
        WHEN m.skill_unit_code LIKE 'SK_COMPAT_%'
            THEN '转入具体正式错因；无法映射则保留证据不足'
        ELSE '要求提交最小反例、首个偏差和修复后边界复测'
    END,
    when_to_use = CASE
        WHEN m.skill_unit_code LIKE 'SK_COMPAT_%'
            THEN '只在接收旧版稳定 ID 时用于兼容路由，不直接生成教学建议。'
        ELSE '当前证据与该症状一致，且能指出代码或状态中的首个偏差时使用。'
    END,
    updated_at = CURRENT_TIMESTAMP
FROM public.ai_standard_mistake_points m
WHERE item.layer = 'MISTAKE_POINT'
  AND item.code = m.code
  AND m.code IN (SELECT code FROM standard_library_v12_repair_targets);

-- V11 课堂场景保留形成性检查；竞赛场景改为约束、复杂度和评测证据核验。
UPDATE public.ai_standard_application_scenarios
SET teacher_move = left(
        '先核对约束画像与复杂度目标，再要求学生提交对拍、边界或评测记录，证明：' || observable_evidence,
        2000
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE enabled = true
  AND context_type = 'CONTEST'
  AND library_version = 'informatics-discipline-application-v2';

DO $$
DECLARE
    baseline standard_library_v12_baseline%ROWTYPE;
    before_business standard_library_v12_business_counts%ROWTYPE;
    after_business standard_library_v12_business_counts%ROWTYPE;
BEGIN
    SELECT * INTO baseline FROM standard_library_v12_baseline;
    SELECT * INTO before_business FROM standard_library_v12_business_counts;

    IF EXISTS (SELECT 1 FROM public.informatics_knowledge_nodes) THEN
        IF baseline.disambiguation_targets <> 33 THEN
            RAISE EXCEPTION 'V12 expected exactly 33 knowledge disambiguation targets, found %', baseline.disambiguation_targets;
        END IF;
        IF NOT ((baseline.meta_repairs = 358 AND baseline.compatibility_meta_repairs = 10)
            OR (baseline.meta_repairs = 0 AND baseline.compatibility_meta_repairs = 0)) THEN
            RAISE EXCEPTION 'V12 expected either the original 358/10 meta-repair baseline or an already governed 0/0 replay baseline, found % and %',
                baseline.meta_repairs, baseline.compatibility_meta_repairs;
        END IF;
        IF baseline.overlap_mistakes <> 2 THEN
            RAISE EXCEPTION 'V12 expected two stable overlap-matching mistakes, found %', baseline.overlap_mistakes;
        END IF;
        IF baseline.v11_contest_scenarios <> 48 THEN
            RAISE EXCEPTION 'V12 expected 48 V11 contest scenarios, found %', baseline.v11_contest_scenarios;
        END IF;
        IF (SELECT count(*) FROM standard_library_v12_knowledge_curations) <> 33
           OR (SELECT count(*) FROM standard_library_v12_repair_targets) <> baseline.meta_repairs THEN
            RAISE EXCEPTION 'V12 curation source tables are incomplete';
        END IF;
    END IF;

    IF EXISTS (
        SELECT name
        FROM public.informatics_knowledge_nodes
        WHERE enabled = true
        GROUP BY name
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'V12 enabled knowledge node names remain ambiguous';
    END IF;

    IF (SELECT count(*)
        FROM public.informatics_knowledge_nodes
        WHERE enabled = true
          AND type = 'KNOWLEDGE_POINT'
          AND description LIKE '细颗粒知识点：%') > 384 THEN
        RAISE EXCEPTION 'V12 template knowledge description debt exceeded 384';
    END IF;

    IF EXISTS (
        SELECT name
        FROM public.ai_standard_mistake_points
        WHERE enabled = true
        GROUP BY name
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'V12 enabled mistake point names remain duplicated';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.ai_standard_mistake_points
        WHERE enabled = true
          AND repair_strategy LIKE '%具体诊断、修正建议和提高建议由 AI%'
    ) THEN
        RAISE EXCEPTION 'V12 meta repair instructions remain in enabled mistakes';
    END IF;

    IF EXISTS (SELECT 1 FROM public.informatics_knowledge_nodes)
       AND ((SELECT count(*)
             FROM public.ai_standard_mistake_points
             WHERE enabled = true
               AND skill_unit_code NOT LIKE 'SK_COMPAT_%'
               AND library_version = 'standard-library-v3-skill-mistake'
               AND (repair_strategy LIKE '针对“%'
                 OR code IN ('MP_V7_STRING_OVERLAPPING_MATCH_SKIPPED',
                             'MP_V8_STRING_OVERLAP_MATCH_SKIPPED'))) <> 348
         OR (SELECT count(*)
             FROM public.ai_standard_mistake_points
             WHERE enabled = true
               AND skill_unit_code LIKE 'SK_COMPAT_%'
               AND repair_strategy LIKE '该条目只保留旧版 code 兼容%') <> 10) THEN
        RAISE EXCEPTION 'V12 actionable repairs or compatibility routing contracts are incomplete';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.ai_standard_mistake_points
        WHERE enabled = true
          AND skill_unit_code NOT LIKE 'SK_COMPAT_%'
          AND code IN (SELECT code FROM standard_library_v12_repair_targets)
          AND length(btrim(COALESCE(repair_strategy, ''))) < 80
    ) THEN
        RAISE EXCEPTION 'V12 formal mistake repair is not actionable enough';
    END IF;

    IF EXISTS (
        SELECT m.code
        FROM public.ai_standard_mistake_points m
        LEFT JOIN public.ai_standard_library_items item
          ON item.layer = 'MISTAKE_POINT' AND item.code = m.code
        WHERE (m.repair_strategy LIKE '针对“%'
            OR m.repair_strategy LIKE '该条目只保留旧版 code 兼容%'
            OR m.code IN ('MP_V7_STRING_OVERLAPPING_MATCH_SKIPPED',
                          'MP_V8_STRING_OVERLAP_MATCH_SKIPPED'))
          AND (item.id IS NULL
            OR item.name IS DISTINCT FROM m.name
            OR item.description IS DISTINCT FROM m.description
            OR item.common_misconception IS DISTINCT FROM m.misconception
            OR item.hintl3 IS DISTINCT FROM left(m.repair_strategy, 800)
            OR item.evidence_signals IS DISTINCT FROM m.symptom)
    ) THEN
        RAISE EXCEPTION 'V12 normalized mistakes and flat snapshots diverged';
    END IF;

    IF EXISTS (
        SELECT transfer_pair_code
        FROM public.ai_standard_application_scenarios
        WHERE enabled = true
          AND library_version = 'informatics-discipline-application-v2'
        GROUP BY transfer_pair_code
        HAVING count(*) <> 2
            OR count(DISTINCT context_type) <> 2
            OR max(teacher_move) FILTER (WHERE context_type = 'CLASSROOM')
               = max(teacher_move) FILTER (WHERE context_type = 'CONTEST')
    ) THEN
        RAISE EXCEPTION 'V12 V11 transfer pairs are incomplete or reuse the same teacher move';
    END IF;

    SELECT
        (SELECT count(*) FROM public.problems),
        (SELECT count(*) FROM public.test_cases),
        (SELECT count(*) FROM public.submissions),
        (SELECT count(*) FROM public.submission_analyses),
        (SELECT count(*) FROM public.submission_diagnosis_facts),
        (SELECT count(*) FROM public.student_ai_feedbacks),
        (SELECT count(*) FROM public.student_recommendation_events)
    INTO after_business;

    IF before_business IS DISTINCT FROM after_business THEN
        RAISE EXCEPTION 'V12 changed business facts while governing library content';
    END IF;
END $$;
