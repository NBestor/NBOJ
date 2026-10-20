-- 学科质量第四批扩充：在没有足够真实学生行为数据时，以高中信息技术课程标准、
-- CCF NOI 大纲、生产结构引用和人工学科审校为依据，细化范围映射并扩充诊断层。
-- 不新增平行知识树，不修改稳定 code，不触碰题目、提交、诊断事实和课堂业务数据。

-- 领域级标准映射向章节级细化；官方来源能直接承接的标 SOURCE_ANCHORED，
-- 本地竞赛实践拆分只标 INFERRED，避免把本地叶子颗粒度伪装成官方逐项表述。
INSERT INTO public.informatics_discipline_scope_mappings (
    knowledge_node_code, framework_code, scope_code, coverage_role,
    source_reference, evidence_note, review_status, enabled,
    created_at, updated_at
)
SELECT n.code, v.framework_code, v.scope_code, v.coverage_role,
       v.source_reference, v.evidence_note, v.review_status, true,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('BASIC.IO', 'MOE_HIGH_SCHOOL_IT_2020', 'PROGRAM_INPUT_OUTPUT', 'FOUNDATION',
     'https://jyj.changdu.gov.cn/cdsjyj/c101476/202205/764ee1914e3c45aaba8e372f1b628a34/files/f97a4fa12a6c4acf94d34755a16a9d22.pdf',
     '高中信息技术“数据与计算”模块中的程序实现需要按输入、处理、输出组织数据流，本地输入输出章节承接该基础能力。', 'SOURCE_ANCHORED'),
    ('BASIC.TYPE', 'MOE_HIGH_SCHOOL_IT_2020', 'DATA_REPRESENTATION_AND_TYPES', 'FOUNDATION',
     'https://jyj.changdu.gov.cn/cdsjyj/c101476/202205/764ee1914e3c45aaba8e372f1b628a34/files/f97a4fa12a6c4acf94d34755a16a9d22.pdf',
     '高中信息技术要求理解数据表示并在程序中选择合适表示方式，本地变量与数据类型章节作为实现入口。', 'SOURCE_ANCHORED'),
    ('BASIC.EXPR', 'MOE_HIGH_SCHOOL_IT_2020', 'EXPRESSIONS_AND_OPERATIONS', 'FOUNDATION',
     'https://jyj.changdu.gov.cn/cdsjyj/c101476/202205/764ee1914e3c45aaba8e372f1b628a34/files/f97a4fa12a6c4acf94d34755a16a9d22.pdf',
     '程序设计中的运算、关系与逻辑表达式用于把数据处理规则转成可执行步骤。', 'SOURCE_ANCHORED'),
    ('BASIC.BRANCH', 'MOE_HIGH_SCHOOL_IT_2020', 'BRANCH_CONTROL', 'FOUNDATION',
     'https://jyj.changdu.gov.cn/cdsjyj/c101476/202205/764ee1914e3c45aaba8e372f1b628a34/files/f97a4fa12a6c4acf94d34755a16a9d22.pdf',
     '课程标准要求用程序设计语言实现算法，分支结构承接条件选择和分类讨论。', 'SOURCE_ANCHORED'),
    ('BASIC.LOOP', 'MOE_HIGH_SCHOOL_IT_2020', 'LOOP_CONTROL', 'FOUNDATION',
     'https://jyj.changdu.gov.cn/cdsjyj/c101476/202205/764ee1914e3c45aaba8e372f1b628a34/files/f97a4fa12a6c4acf94d34755a16a9d22.pdf',
     '课程标准要求掌握程序基本控制结构，循环章节承接重复处理、边界与终止条件。', 'SOURCE_ANCHORED'),
    ('BASIC.ARRAY', 'MOE_HIGH_SCHOOL_IT_2020', 'DATA_ORGANIZATION_ARRAY', 'FOUNDATION',
     'https://jyj.changdu.gov.cn/cdsjyj/c101476/202205/764ee1914e3c45aaba8e372f1b628a34/files/f97a4fa12a6c4acf94d34755a16a9d22.pdf',
     '“数据与数据结构”模块覆盖数组等数据组织方式，本地数组章节承接下标、遍历和二维表示。', 'SOURCE_ANCHORED'),
    ('BASIC.STRING', 'MOE_HIGH_SCHOOL_IT_2020', 'TEXT_DATA_PROCESSING', 'FOUNDATION',
     'https://jyj.changdu.gov.cn/cdsjyj/c101476/202205/764ee1914e3c45aaba8e372f1b628a34/files/f97a4fa12a6c4acf94d34755a16a9d22.pdf',
     '文本是高中数据处理的基本对象，字符串章节承接字符、子串、匹配与构造。', 'SOURCE_ANCHORED'),
    ('BASIC.FUNCTION', 'MOE_HIGH_SCHOOL_IT_2020', 'FUNCTIONAL_DECOMPOSITION', 'FOUNDATION',
     'https://jyj.changdu.gov.cn/cdsjyj/c101476/202205/764ee1914e3c45aaba8e372f1b628a34/files/f97a4fa12a6c4acf94d34755a16a9d22.pdf',
     '计算思维要求分解问题并用程序实现，函数章节承接模块边界、参数和返回合同。', 'SOURCE_ANCHORED'),
    ('BASIC.RECURSION', 'MOE_HIGH_SCHOOL_IT_2020', 'ITERATION_AND_RECURSION', 'EXTENSION',
     'https://jyj.changdu.gov.cn/cdsjyj/c101476/202205/764ee1914e3c45aaba8e372f1b628a34/files/f97a4fa12a6c4acf94d34755a16a9d22.pdf',
     '“数据与数据结构”模块要求理解迭代与递归，本地递归章节承接终止、状态推进和结果合并。', 'SOURCE_ANCHORED'),
    ('ENG.DEBUG', 'MOE_HIGH_SCHOOL_IT_2020', 'PROGRAM_DEBUGGING_AND_VALIDATION', 'FOUNDATION',
     'https://jyj.changdu.gov.cn/cdsjyj/c101476/202205/764ee1914e3c45aaba8e372f1b628a34/files/f97a4fa12a6c4acf94d34755a16a9d22.pdf',
     '课程标准将调试、修改和验证程序作为程序实现过程的一部分，本地调试方法章节承接可复现验证。', 'SOURCE_ANCHORED'),
    ('ENG.COMPLEXITY', 'MOE_HIGH_SCHOOL_IT_2020', 'ALGORITHM_EFFICIENCY_AWARENESS', 'EXTENSION',
     'https://jyj.changdu.gov.cn/cdsjyj/c101476/202205/764ee1914e3c45aaba8e372f1b628a34/files/f97a4fa12a6c4acf94d34755a16a9d22.pdf',
     '课程标准要求体验和比较算法效率，本地复杂度章节承接时间、空间与数据规模判断。', 'SOURCE_ANCHORED'),
    ('BASIC.IO', 'CCF_NOI_2025', 'CPP_INPUT_OUTPUT', 'FOUNDATION',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     'NOI 入门程序设计范围包含标准输入输出和格式处理。', 'SOURCE_ANCHORED'),
    ('BASIC.TYPE', 'CCF_NOI_2025', 'CPP_BASIC_TYPES', 'FOUNDATION',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     'NOI 入门程序设计范围包含基本数据类型、变量和数值范围。', 'SOURCE_ANCHORED'),
    ('BASIC.EXPR', 'CCF_NOI_2025', 'CPP_EXPRESSIONS_OPERATORS', 'FOUNDATION',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     'NOI 入门程序设计范围包含算术、关系、逻辑与位运算表达式。', 'SOURCE_ANCHORED'),
    ('BASIC.BRANCH', 'CCF_NOI_2025', 'CPP_BRANCH_STATEMENTS', 'FOUNDATION',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     'NOI 入门程序设计范围包含条件分支和多情况判断。', 'SOURCE_ANCHORED'),
    ('BASIC.LOOP', 'CCF_NOI_2025', 'CPP_LOOP_STATEMENTS', 'FOUNDATION',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     'NOI 入门程序设计范围包含循环结构、嵌套与控制语句。', 'SOURCE_ANCHORED'),
    ('BASIC.ARRAY', 'CCF_NOI_2025', 'CPP_ARRAYS', 'FOUNDATION',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     'NOI 入门程序设计范围包含一维、二维数组及其遍历。', 'SOURCE_ANCHORED'),
    ('BASIC.STRING', 'CCF_NOI_2025', 'CPP_STRINGS', 'FOUNDATION',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     'NOI 入门程序设计范围包含字符、字符串和基本处理。', 'SOURCE_ANCHORED'),
    ('BASIC.FUNCTION', 'CCF_NOI_2025', 'CPP_FUNCTIONS', 'FOUNDATION',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     'NOI 入门程序设计范围包含函数定义、参数和返回值。', 'SOURCE_ANCHORED'),
    ('BASIC.RECURSION', 'CCF_NOI_2025', 'CPP_RECURSION', 'EXTENSION',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     'NOI 大纲把递归作为程序设计和算法实现的重要基础。', 'SOURCE_ANCHORED'),
    ('MATH.NUMBER', 'CCF_NOI_2025', 'ENTRY_NUMBER_THEORY', 'EXTENSION',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     'NOI 入门数学范围包含整除、质数、最大公约数和模运算等数论基础。', 'SOURCE_ANCHORED'),
    ('MATH.COUNT', 'CCF_NOI_2025', 'ENTRY_COMBINATORICS', 'EXTENSION',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     'NOI 入门数学范围包含排列组合、计数和容斥基础。', 'SOURCE_ANCHORED'),
    ('MATH.BIT', 'CCF_NOI_2025', 'ENTRY_BIT_OPERATIONS', 'EXTENSION',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     'NOI 程序设计与数学范围涉及二进制表示和位运算。', 'SOURCE_ANCHORED'),
    ('MATH.GEOMETRY', 'CCF_NOI_2025', 'ENTRY_GEOMETRY_COORDINATES', 'EXTENSION',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     'NOI 入门数学范围包含坐标、距离与基础几何关系。', 'SOURCE_ANCHORED'),
    ('CONTEST.READING', 'CCF_NOI_2025', 'PROBLEM_READING_AND_MODELING', 'PRACTICE',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     '本地读题与建模章节把大纲中的问题求解要求拆成目标、输入、输出和约束检查，属于教学推导映射。', 'INFERRED'),
    ('CONTEST.SUBMIT', 'CCF_NOI_2025', 'SOLUTION_VALIDATION_AND_REVIEW', 'PRACTICE',
     'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf',
     '本地提交检查与失败复盘把竞赛问题求解中的正确性、资源限制和验证要求转成练习流程，属于教学推导映射。', 'INFERRED')
) AS v(knowledge_node_code, framework_code, scope_code, coverage_role,
       source_reference, evidence_note, review_status)
JOIN public.informatics_knowledge_nodes n
  ON n.code = v.knowledge_node_code AND n.enabled = true
ON CONFLICT (knowledge_node_code, framework_code, scope_code) DO UPDATE SET
    coverage_role = EXCLUDED.coverage_role,
    source_reference = EXCLUDED.source_reference,
    evidence_note = EXCLUDED.evidence_note,
    review_status = EXCLUDED.review_status,
    enabled = true,
    updated_at = CURRENT_TIMESTAMP;

-- 四领域各 12 个叶子知识点：描述概念边界，目标可观察，题型可验证。
UPDATE public.informatics_knowledge_nodes n
SET description = curated.description,
    learning_objectives = curated.learning_objectives,
    typical_problems = curated.typical_problems,
    aliases = curated.aliases,
    prerequisites = curated.prerequisites,
    library_version = 'informatics-knowledge-discipline-v4',
    updated_at = CURRENT_TIMESTAMP
FROM (VALUES
    ('BASIC.IO.STDIN.输入顺序映射',
     '输入顺序映射是把题面中的字段、重复次数和嵌套结构转换为确定的读取序列；每次读取都必须消费预期数量的 token 或整行，并与后续字段保持同一游标，不能只按样例排版猜测换行。',
     E'能把输入说明写成字段、类型、数量、层级四列表。\n能逐步核对读取游标，并区分按 token 读取与按整行读取。',
     E'先读 n 再读 n 个元素和 q 次询问\n混合数字与带空格字符串\n漏读一个字段导致后续整体错位',
     E'输入模式\n输入字段映射\ninput schema\ntoken consumption order', NULL),
    ('BASIC.BRANCH.CASE.覆盖所有情况',
     '分类讨论必须让每个合法输入恰好进入一个符合题意的分支；检查时先列出全集和边界，再验证各条件是否互斥、是否完整覆盖以及默认分支是否只承接真正剩余情况。',
     E'能用区间或集合写出每个分支覆盖范围。\n能用边界值、相邻值和未命中值检查重叠与遗漏。',
     E'成绩等级划分\n按符号和奇偶分类\n条件顺序使宽分支吞掉窄分支',
     E'分支完备性\ncase coverage\nexhaustive cases', 'BASIC.EXPR.COMPARE.边界比较'),
    ('BASIC.LOOP.BOUNDARY.最后一次迭代',
     '最后一次迭代由循环不变量和终止条件共同决定：循环体使用的最大下标、最后一对元素或最后一个状态必须仍合法；循环结束后还要判断是否存在需要单独结算的尾段。',
     E'能从循环条件推导第一次和最后一次进入时变量值。\n能检查循环体中的 i+1、i-1 和尾段结算是否越界或遗漏。',
     E'遍历相邻元素只到 n-2\n游程编码结算最后一段\n循环少做或多做一次',
     E'末次循环\nfinal iteration\nloop endpoint audit', 'BASIC.LOOP.BOUNDARY.左闭右开'),
    ('BASIC.ARRAY.MATRIX.边界检查',
     '二维数组边界检查必须在访问前确认行下标位于 [0,rows)、列下标位于 [0,cols)；非方阵中 rows 与 cols 不能互换，方向移动产生的新坐标也不能先取值后判界。',
     E'能为任意候选坐标写出完整行列合法条件。\n能用 1×n、n×1、非方阵和四角位置验证访问顺序。',
     E'网格四方向访问\n非方阵遍历\n先读取 grid[nr][nc] 再检查范围',
     E'矩阵判界\ngrid bounds check\ncheck before access', 'BASIC.ARRAY.MATRIX.行列含义'),
    ('BASIC.ARRAY.MATRIX.方向遍历',
     '方向遍历用成对的行偏移和列偏移生成相邻位置；方向数组的顺序、坐标含义和允许移动集合必须与题面一致，每个候选位置都要经过边界、障碍和访问状态检查。',
     E'能把每个方向向量回译为实际移动。\n能追踪候选坐标从生成到判界、判障碍和入队的完整顺序。',
     E'四联通与八联通网格\n行列偏移写反\n漏掉一个方向或重复一个方向',
     E'方向数组遍历\ndirection vectors\ngrid neighbor iteration', 'BASIC.ARRAY.MATRIX.边界检查'),
    ('BASIC.ARRAY.PREFIX.计数数组',
     '计数数组用下标表示离散类别、数组值表示出现次数；使用前必须确认值域可控、下标映射无歧义、初值为零，并让每个输入元素恰好贡献一次。',
     E'能说明计数数组每个下标对应的原始值。\n能用总频次等于输入元素数验证遗漏、重复和偏移。',
     E'小值域整数频次\n字符计数\n负数或大值域需要偏移或改用 map',
     E'频次数组\nhistogram array\ncounting array', 'BASIC.ARRAY.INDEX.下标映射'),
    ('BASIC.ARRAY.UPDATE.临时数组',
     '当新状态中多个位置都依赖同一轮旧状态时，临时数组负责隔离“读旧”和“写新”；一轮全部计算完成后再整体交换或复制，避免前面更新污染后面计算。',
     E'能指出每个新值依赖旧数组的哪些位置。\n能判断是否需要双缓冲，并验证交换后下一轮读取对象正确。',
     E'细胞自动机同步更新\n一维状态转移\n原地修改导致同轮读取新旧混合值',
     E'双缓冲\ntemporary buffer\nread old write new', 'BASIC.ARRAY.UPDATE.读旧写新分离'),
    ('BASIC.FUNCTION.DEF.返回值设计',
     '函数返回值应表达调用者需要继续使用的结果或状态，输出到屏幕、修改参数和返回值是三种不同合同；所有合法执行路径都必须返回与声明类型和语义一致的值。',
     E'能用一句话说明返回值的类型、含义和失败表示。\n能检查每条分支是否返回，并区分打印结果与把结果交给调用者。',
     E'查找函数返回位置或 -1\n判断函数返回布尔值\n只打印答案导致调用方得到空值',
     E'函数返回合同\nreturn value contract\nresult semantics', 'BASIC.FUNCTION.DEF.参数设计'),
    ('BASIC.STRING.MATCH.统计出现次数',
     '统计子串出现次数需要先确定是否允许重叠、大小写是否敏感和空模式是否合法；每次命中后下一次搜索起点由这些规则决定，不能固定跳过整个模式长度。',
     E'能写出一次命中后搜索指针的更新规则。\n能用重叠、相邻、不存在和首尾命中样例验证计数。',
     E'统计 aba 在 ababa 中的出现次数\n大小写敏感匹配\n命中后推进过远漏掉重叠结果',
     E'子串计数\noccurrence counting\nsubstring frequency', 'BASIC.STRING.MATCH.查找出现位置'),
    ('BASIC.STRING.MATCH.重叠匹配计数',
     '重叠匹配允许后一次命中与前一次共享字符，因此命中后通常从当前起点的下一位置继续，或由匹配算法的前后缀状态决定回退；推进一个完整模式长度会漏解。',
     E'能画出相邻两次命中共享的字符区间。\n能对朴素查找和前缀状态分别说明命中后的继续位置。',
     E'aaa 中统计 aa\n周期字符串中的重叠模式\n命中后 i+=pattern.length 导致漏计',
     E'可重叠匹配\noverlapping matches\noverlap-aware counting', 'BASIC.STRING.MATCH.统计出现次数'),
    ('BASIC.STRING.SUBSTRING.长度参数',
     '子串 API 的第二参数可能表示长度或结束位置；调用前必须按所用语言合同换算，并固定区间是左闭右开还是闭区间，避免把 end 直接当 length。',
     E'能从起点和终点推导长度，或从起点和长度推导终点。\n能用空串、单字符、到末尾和越界请求验证 API 行为。',
     E'C++ substr(pos,len)\nPython s[l:r]\n把 r 作为长度造成多取字符',
     E'子串长度参数\nsubstring length argument\nslice endpoint conversion', 'BASIC.STRING.SUBSTRING.起止位置'),
    ('BASIC.TYPE.INTEGER.整型溢出',
     '整型溢出发生在某个中间运算超出当前操作数类型范围时，即使最终结果或接收变量更宽也可能已经失真；必须估算表达式树中乘法、累加和哨兵加法的最大量级。',
     E'能为输入、单次乘积、累计和与哨兵分别估算范围。\n能在运算前提升类型，并用极值样例验证中间结果。',
     E'n 个大整数求和\n两个 int 相乘后赋给 long long\nINF 加权值发生回绕',
     E'整数溢出\ninteger overflow\nintermediate range', 'BASIC.TYPE.INTEGER.int_范围'),

    ('MATH.NUMBER.GCD.除零边界',
     '欧几里得算法只在除数非零时执行取余；循环条件应先保护除数，0 与非零数的最大公约数等于非零数的绝对值，输入符号应先规范化。',
     E'能手算包含 0、负数和相等数的 gcd 过程。\n能解释循环结束时哪个变量保存结果以及为何不会除零。',
     E'gcd(0,a)\n负数输入规范化\n交换或循环条件错误导致 a%0',
     E'最大公约数零边界\ngcd zero case\nEuclidean algorithm guard', 'MATH.NUMBER.GCD.欧几里得算法'),
    ('MATH.NUMBER.GCD.最小公倍数',
     '非零整数的最小公倍数可由 |a/gcd(a,b)×b| 计算；先除后乘缩小中间量，任一输入为 0 时结果为 0，同时仍要检查最终乘积是否超出类型范围。',
     E'能从 gcd 关系推导 lcm 公式。\n能比较先乘后除与先除后乘的中间量级，并处理 0 和符号。',
     E'计算两个数的 lcm\n大整数先乘后除溢出\n0 与非零数的最小公倍数',
     E'最小公倍数\nlcm\nleast common multiple', 'MATH.NUMBER.GCD.欧几里得算法'),
    ('MATH.NUMBER.MOD.模意义保持',
     '在只关心余数的加法、减法和乘法中，可以在每一步取模并保持最终同余类；但大小比较、除法和原始数值信息通常不能仅凭余数恢复，使用前必须确认运算与目标允许取模。',
     E'能说明哪些运算保持同余以及何时不能提前取模。\n能规范化负余数并检查中间乘积在取模前的范围。',
     E'大数方案数逐步取模\n用余数比较原数大小\n减法后结果为负的规范化',
     E'同余保持\nmodular equivalence\ncongruence preservation', NULL),
    ('MATH.NUMBER.DIVISIBILITY.平方根优化',
     '若 d 是 n 的因子，则 n/d 与它成对出现，因此试除只需覆盖 d×d≤n；使用 d×d 时要防中间溢出，也可以写成 d≤n/d，并单独处理完全平方数的重复因子。',
     E'能证明因子为何在平方根两侧成对。\n能处理完全平方数并选择不会溢出的循环条件。',
     E'试除判断质数\n枚举全部因子\n完全平方数的平方根只计一次',
     E'平方根试除\nsquare-root bound\nfactor pair optimization', NULL),
    ('MATH.COUNT.PERM.排列',
     '排列计数关注有序位置：同一批对象交换位置后若结果改变，就属于不同方案；还要明确对象是否互异、是否允许重复选择以及是否只排列部分位置。',
     E'能用交换两个位置判断顺序是否重要。\n能区分全排列、部分排列和允许重复的有序序列。',
     E'n 个不同对象全排列\n从 n 个对象选 k 个依次放置\n可重复数字组成长度 k 的序列',
     E'排列\npermutation\nordered arrangement', 'MATH.COUNT.PERM.顺序是否重要'),
    ('MATH.COUNT.PERM.组合',
     '组合计数只关心选中了哪些对象，不关心选择顺序；同一集合的不同挑选顺序应归为一个方案，并需明确是否允许重复元素和对象是否可区分。',
     E'能把一个有序选择结果归一化为无序集合。\n能判断题目使用组合、可重组合还是其他计数模型。',
     E'从 n 人中选 k 人\n选若干物品不计顺序\n把同一组合的 k! 种顺序重复计数',
     E'组合\ncombination\nunordered selection', 'MATH.COUNT.PERM.顺序是否重要'),
    ('MATH.COUNT.PERM.除重',
     '当计数对象中存在相同值或对称结构时，交换这些不可区分对象不会产生新方案；除重可以通过频次公式、排序后跳过同层重复选择或规范化表示完成，不能事后凭感觉相除。',
     E'能指出哪些对象可区分、哪些交换后结果不变。\n能用小规模枚举对照除重前后方案集合。',
     E'含重复字符的排列\n回溯同层去重\n把值相同但身份不同的对象错误合并',
     E'重复排列除重\ndeduplication\nmultiset permutation', 'MATH.COUNT.PERM.排列'),
    ('MATH.COUNT.INCLUSION.二集合容斥',
     '两个集合并集大小等于各自大小之和减去交集大小，因为交集元素在相加时被计算了两次；应用前必须明确全集、两个条件集合和交集的实际含义。',
     E'能在维恩图中标出重复计算区域。\n能从至少满足一个条件、同时满足和都不满足之间转换。',
     E'满足 A 或 B 的人数\n能被 2 或 3 整除的数\n遗漏交集导致重复计数',
     E'二集合容斥\ntwo-set inclusion-exclusion\nunion counting', 'MATH.COUNT.INCLUSION.集合重叠'),
    ('MATH.BIT.MASK.位数限制',
     '用整数掩码表示 k 个布尔状态时，需要至少 k 个有效二进制位；k 必须小于所用类型的位宽，1<<k 的操作数类型也要足够宽，状态总数 2^k 还必须满足时间和空间限制。',
     E'能根据对象数量选择掩码类型并估算状态总数。\n能检查最高位移位、符号位和容器规模。',
     E'20 个对象的子集状态\n1<<k 使用窄整型\nk 过大导致状态空间爆炸',
     E'掩码位宽\nbit width limit\nmask capacity', 'MATH.BIT.BINARY.位权'),
    ('MATH.BIT.MASK.枚举子集',
     '枚举一个掩码的全部子集时，常用 sub=(sub-1)&mask 逐步删除位；循环必须明确是否包含 mask 和空集，并在 sub=0 时停止，避免无符号回绕后无限循环。',
     E'能手算一个三位 mask 的完整子集序列。\n能写出包含或排除空集的终止合同，并估算总枚举次数。',
     E'枚举给定集合的所有子集\n漏掉空集或全集\nsub=0 后继续导致回绕',
     E'子掩码枚举\nsubmask enumeration\nsubset iteration', 'MATH.BIT.MASK.集合掩码'),
    ('MATH.GEOMETRY.DIST.曼哈顿距离',
     '曼哈顿距离 |x1-x2|+|y1-y2| 表示只能沿坐标轴移动时的最少步数；它与欧氏直线距离不同，坐标是矩阵行列时还要先明确哪个量对应哪一轴。',
     E'能从允许移动方式判断是否适用曼哈顿距离。\n能用负坐标、同轴点和矩阵坐标验证绝对值与轴顺序。',
     E'网格四方向最短步数\n出租车距离\n误用平方和或漏写绝对值',
     E'城市街区距离\nManhattan distance\nL1 distance', 'MATH.GEOMETRY.COORD.行列坐标'),
    ('MATH.GEOMETRY.RELATION.边界包含',
     '判断点或区间是否位于几何对象内时，必须先明确边界是否算作内部；包含边界使用非严格比较，不包含边界使用严格比较，并分别检查每条边或每个端点。',
     E'能把“内部”“内部或边界”翻译成不同不等式。\n能用恰在边、角点和边外一单位的点验证条件。',
     E'点在矩形内或边界上\n区间端点是否相交\n严格比较错误排除边界点',
     E'边界点包含\nboundary inclusion\nclosed region test', 'BASIC.EXPR.COMPARE.边界比较'),

    ('ENG.ERROR.RUNTIME.空容器访问保护',
     '读取 front、back、top、首元素或末元素前必须证明容器非空；保护条件应位于访问之前，并覆盖经过删除、过滤或最小输入后变空的路径。',
     E'能为每次容器读取指出非空证据。\n能用空输入、删到最后一个元素和过滤后为空复现风险。',
     E'空队列读取 front\n空 vector 访问 a[0]\nwhile 条件中先 top 后 empty',
     E'空容器保护\nempty-container guard\ncheck before read', 'ENG.DEBUG.BOUNDARY.最小输入'),
    ('ENG.ERROR.RUNTIME.递归爆栈',
     '递归爆栈来自调用深度乘以单层栈帧超过运行环境限制；即使存在正确出口，链式输入、退化树或过大的局部数组仍可能让深度或单层空间失控。',
     E'能估算最坏递归深度和单层局部空间。\n能识别应改迭代、显式栈或缩小栈帧的场景。',
     E'链式图 DFS 深度为 n\n递归函数内定义大数组\n分治深度与线性递归深度比较',
     E'栈溢出\nstack overflow\nrecursion depth failure', 'BASIC.RECURSION.BASE.终止条件'),
    ('ENG.ERROR.TIME.复杂度过高',
     '复杂度过高是算法操作数量在最大数据规模下超过时间预算，而不是只在本机运行稍慢；判断时要代入 n、m、q、状态数和循环嵌套，并区分数量级问题与常数问题。',
     E'能把复杂度表达式代入最大约束估算操作量。\n能指出瓶颈循环或状态维度，并提出数量级更低的替代方案。',
     E'n=2×10^5 使用 O(n^2)\n每次查询全量扫描\n只改快读无法修复指数搜索',
     E'超时复杂度\ntime limit exceeded\nasymptotic bottleneck', 'ENG.COMPLEXITY.TIME.数据范围反推'),
    ('ENG.ERROR.WRONG_ANSWER.样例通过隐藏失败',
     '样例通过只证明少量公开输入下输出一致，隐藏数据还会覆盖最小规模、极值、重复、并列、特殊结构和最大规模；应从题目约束主动构造类别，而不是继续改到样例相同。',
     E'能从约束列出样例未覆盖的输入类别。\n能为每类写出期望并定位第一处状态分叉。',
     E'样例无重复但隐藏数据有重复\n样例规模小未触发溢出\n并列规则只在隐藏数据出现',
     E'样例过拟合\nsample passes hidden fails\nhidden case coverage', 'ENG.DEBUG.BOUNDARY.最小输入'),
    ('ENG.ERROR.WRONG_ANSWER.状态未重置',
     '状态未重置是本应属于单个测试、分支或查询的变量继续携带旧值；需要按生命周期区分全局预处理、每组状态和每次迭代状态，并在正确层级完整初始化。',
     E'能给数组、累计量、标记和容器标注生命周期。\n能用第二组最小输入验证上一组状态没有残留。',
     E'visited 跨测试未清空\n答案累计量在组外初始化\n只重置前 n 个位置但新一组范围更大',
     E'状态残留\nstale state between cases\ncase isolation', 'BASIC.IO.MULTI_CASE.每组状态重置'),
    ('ENG.ERROR.WRONG_ANSWER.边界遗漏',
     '边界遗漏指主逻辑只处理一般位置，却没有覆盖空、单元素、首尾、等号、最大值或最后一段；修复应从定义补全边界合同，而不是为某个失败样例临时加特判。',
     E'能从每个下标、区间和比较条件列出边界集合。\n能说明统一逻辑是否覆盖边界，必要特判由哪条定义产生。',
     E'n=0 或 n=1\n区间端点恰好相等\n循环结束忘记结算尾段',
     E'边界条件遗漏\nmissing boundary case\noff-by-one omission', 'BASIC.EXPR.COMPARE.边界比较'),
    ('ENG.DEBUG.BOUNDARY.最大输入',
     '最大输入测试把题目给出的 n、m、q、值域和结构深度同时推到允许上限，用来验证时间、内存、递归深度和中间数值，而不是实际打印或手工构造全部内容才算测试。',
     E'能从约束推导最坏操作数、元素数和中间量级。\n能用生成器或估算表验证资源预算。',
     E'最大 n 与最大 q 同时出现\n退化树达到最大深度\n最大权值路径和溢出',
     E'压力测试\nmaximum-scale test\nstress boundary', 'ENG.COMPLEXITY.TIME.数据范围反推'),
    ('ENG.DEBUG.TRACE.数组状态',
     '数组状态追踪按每一步记录被读取的旧位置、写入的新位置和完整关键区间，并在第一次与手算期望不同处停止；只打印最终数组无法区分初始化、索引或更新顺序错误。',
     E'能选择足以解释更新的下标和值。\n能在第一处偏差定位对应读写操作和数组版本。',
     E'前缀和逐项构造\n双缓冲数组更新\n排序一轮后的区间状态',
     E'数组轨迹\narray state trace\nfirst divergence', 'ENG.DEBUG.SAMPLE.手算对照'),
    ('ENG.DEBUG.TRACE.队列变化',
     '队列追踪记录每次入队来源、出队元素、队首队尾和去重时机；在 BFS 或事件模拟中，它能判断元素是未入队、重复入队还是以错误顺序处理。',
     E'能回放队列的完整入出序列。\n能把 visited 更新时机与重复入队现象对应起来。',
     E'BFS 层次推进\n拓扑队列入度降为零\n入队前未判重导致队列膨胀',
     E'队列轨迹\nqueue trace\nenqueue dequeue log', 'DS.LINEAR.QUEUE.先进先出'),
    ('ENG.COMPLEXITY.SPACE.二维空间',
     '二维结构的空间由行数×列数×单元素字节数决定，还要计入多张表、容器对象开销和是否分配在栈上；能通过滚动数组降维的前提是后续只依赖有限相邻层。',
     E'能计算二维数组和多份状态表的基础内存。\n能从依赖关系判断是否可滚动，并保证覆盖顺序安全。',
     E'n×m 网格内存估算\n二维 DP 超内存\n错误滚动覆盖仍需读取的旧层',
     E'二维内存\n2D memory footprint\ntable space', 'ENG.COMPLEXITY.SPACE.数组规模'),
    ('ENG.COMPLEXITY.TRADEOFF.预处理收益判断',
     '预处理只有在一次构建成本能被足够多查询复用、额外空间可承受且输入在查询间保持有效时才有收益；应比较“预处理+全部查询”和“逐次直接计算”的总成本。',
     E'能列出构建、单次查询、查询次数和空间四项预算。\n能求出预处理开始获益的查询数量级，并检查数据是否会变化。',
     E'前缀和回答多次区间查询\n只有一次查询却构建大表\n数据更新后继续使用过期预处理',
     E'预处理盈亏平衡\npreprocessing break-even\nquery amortization', 'ENG.COMPLEXITY.TIME.数据范围反推'),
    ('ENG.ERROR.FORMAT.调试输出',
     '调试输出会把额外文本、数组或日志混入评测输出，即使核心答案正确也会格式错误；提交前应让诊断日志走可关闭通道，最终输出只包含题面要求字段。',
     E'能逐项对照题面输出合同和程序所有输出语句。\n能区分本地调试日志与正式答案并在发布模式关闭前者。',
     E'多打印变量标签\n循环内残留数组输出\n标准错误与标准输出混用环境差异',
     E'多余调试信息\ndebug output leakage\nextra output', 'BASIC.IO.STDOUT.按要求换行'),

    ('CONTEST.READING.CONSTRAINT.数据范围',
     '数据范围不仅给出数组大小，还定义可接受的算法数量级、整数类型、递归深度和测试构造上限；应把每个 n、m、q、值域代入候选方案，而不是只看最大的单个数字。',
     E'能把所有约束整理成时间、空间和数值预算。\n能据此排除明显不可行方案并保留可验证基线。',
     E'n 与 q 同时为 2×10^5\n边数 m 决定图算法成本\n值域决定计数数组是否可行',
     E'约束规模\nconstraint budget\ninput limits', NULL),
    ('CONTEST.READING.CONSTRAINT.隐藏特殊条件',
     '隐藏特殊条件是散落在定义、保证、注释或输出规则中的限制，例如有序、互异、连通、答案存在或允许多解；它会直接改变算法前提和边界，不能只从公式段落读取。',
     E'能在题面中标出所有保证和例外。\n能说明删除某个保证后候选算法为何失效或需要补充处理。',
     E'保证图连通\n输入序列已经有序\n答案不唯一且任意一个均可',
     E'题面保证\nhidden constraint\nproblem guarantee', 'CONTEST.READING.CONSTRAINT.数据范围'),
    ('CONTEST.READING.CONSTRAINT.内存限制',
     '内存限制需要换算为可存储元素数量，并计入多份数组、二维表、容器节点、递归栈和运行时开销；只看单个数组的理论大小会低估峰值。',
     E'能把 MB 限制换算成主要数据结构预算。\n能识别峰值同时存在的结构并预留运行时余量。',
     E'256MB 下多张 int 数组\n邻接矩阵与邻接表选择\n递归栈和全局数组同时占用',
     E'内存预算\nmemory limit\npeak memory budget', 'ENG.COMPLEXITY.SPACE.数组规模'),
    ('CONTEST.READING.INPUT.数据组数',
     '数据组数决定外层处理合同：可能显式给 T、读到文件结束或由终止标记结束；每组的字段结构和状态生命周期必须单独定义，不能把首个数一律当作 T。',
     E'能判断三种多组输入形式并写出停止条件。\n能让每组读取数量和状态重置位置保持一致。',
     E'首行给 T\n读到 EOF 的未知组数\n以 0 0 作为终止标记',
     E'测试组数\ntest case count\nmulti-case contract', 'BASIC.IO.STDIN.输入顺序映射'),
    ('CONTEST.READING.OUTPUT.多答案要求',
     '多答案题必须先区分“输出任意合法解”“输出全部解”“按指定顺序选择一个解”和“特殊判题校验”；程序只需满足题面合同，不应把样例方案当成唯一答案。',
     E'能写出输出的合法性条件、数量要求和顺序规则。\n能构造两个不同但都合法的答案验证理解。',
     E'任意一组可行方案\n输出字典序最小方案\n特殊判题下与样例不同仍正确',
     E'非唯一答案\nmultiple valid outputs\noutput contract', 'CONTEST.READING.OUTPUT.输出是否可行'),
    ('CONTEST.READING.OUTPUT.输出路径',
     '输出路径通常需要在求值过程中保存前驱、决策或父状态，再从终点反向恢复并按要求翻转；最优值正确不代表路径一定可恢复，前驱更新必须与最终最优状态一致。',
     E'能说明每个状态保存的前驱含义。\n能从终点回溯到起点，并验证相邻步骤合法、长度和值一致。',
     E'最短路输出节点序列\nDP 恢复选择方案\n更新距离却忘记同步前驱',
     E'方案重建\npath reconstruction\npredecessor trace', 'CONTEST.READING.OUTPUT.输出是否可行'),
    ('CONTEST.SUBMIT.CHECKLIST.复杂度检查',
     '提交前复杂度检查把最坏数据范围代入主循环、状态数和容器操作，确认时间与空间都在预算内；不能用样例运行时间或“看起来只有两层循环”替代分析。',
     E'能标出主耗时路径并给出数量级。\n能同时核对最坏操作数、内存和递归深度。',
     E'嵌套循环实际为 O(nm)\nmap 操作带对数因子\n状态压缩的 2^k 状态数',
     E'提交复杂度复查\ncomplexity checklist\nresource audit', 'CONTEST.READING.CONSTRAINT.数据范围'),
    ('CONTEST.SUBMIT.CHECKLIST.边界复测',
     '边界复测至少覆盖最小规模、最大规模、极值、空或单元素、重复、并列和端点相等；每个样例应对应一条具体合同，而不是随机多跑几个输入。',
     E'能从代码中的下标、除法、循环和比较生成边界清单。\n能为每个边界先写期望结果再运行程序。',
     E'n=1 与 n=最大值\n全部元素相等\n答案恰在区间端点',
     E'边界回归\nboundary retest\nedge-case matrix', 'CONTEST.SUBMIT.CHECKLIST.样例复测'),
    ('CONTEST.SUBMIT.CHECKLIST.溢出风险复查',
     '溢出复查应检查输入类型、乘法、累计和、距离、计数和哨兵运算的中间范围；把结果变量改宽并不能修复已经在窄类型中完成的运算。',
     E'能为每个高风险表达式写出最大绝对值。\n能在运算前提升类型并避免 INF 参与无保护加法。',
     E'边权和超过 int\n组合计数快速增长\nint 乘法后赋给 long long',
     E'数值范围复查\noverflow checklist\nintermediate value audit', 'BASIC.TYPE.INTEGER.整型溢出'),
    ('CONTEST.SUBMIT.CHECKLIST.多组状态复查',
     '多组状态复查要求所有每组数组、容器、累计量、标记和答案在新一组开始前恢复初始状态，同时保留真正可共享的只读预处理；清空范围必须覆盖上一组可能使用的全部位置。',
     E'能把变量分成跨组共享、每组重置和循环内更新三类。\n能用先大后小两组数据暴露部分清空问题。',
     E'第二组沿用 visited\n先 n=100 再 n=3 时尾部残留\n误清空共享预处理导致超时',
     E'多测状态隔离\nmulti-case reset\ntest isolation', 'BASIC.IO.MULTI_CASE.每组状态重置'),
    ('CONTEST.SUBMIT.REVIEW.最小反例构造',
     '最小反例是在仍能触发错误的前提下尽量减少规模、取值种类和结构复杂度，使期望与实际的第一处分叉可以手算；缩减时每次只改变一个因素并保留失败。',
     E'能从失败数据逐步删除元素或简化数值。\n能记录每次缩减是否仍失败并定位最早错误状态。',
     E'把随机大数组缩成 3 个元素\n把复杂图缩成一条关键路径\n一次改多个条件导致无法判断根因',
     E'反例最小化\nminimal counterexample\ndelta debugging', 'ENG.DEBUG.SAMPLE.手算对照'),
    ('CONTEST.SUBMIT.REVIEW.错误类型定位',
     '错误类型定位先依据编译信息、退出状态、实际输出和资源记录区分 CE、RE、WA、TLE、MLE 或格式问题，再进入对应证据链；错误类型只是入口，不是最终根因。',
     E'能为每种评测状态指出第一条可靠证据。\n能把外部错误类型继续缩小到具体代码、状态或复杂度原因。',
     E'RE 来自空容器访问\nWA 来自边界遗漏\nTLE 来自数量级而非输入输出格式',
     E'评测状态分类\nverdict triage\nfailure classification', 'CONTEST.SUBMIT.CHECKLIST.样例复测')
) AS curated(code, description, learning_objectives, typical_problems, aliases, prerequisites)
WHERE n.code = curated.code
  AND n.enabled = true
  AND n.type = 'KNOWLEDGE_POINT';

CREATE TEMP TABLE discipline_quality_v4_mistakes (
    code character varying(100) PRIMARY KEY,
    category character varying(80) NOT NULL,
    name character varying(120) NOT NULL,
    description character varying(1600) NOT NULL,
    skill_unit_code character varying(160) NOT NULL,
    mistake_type character varying(80) NOT NULL,
    misconception character varying(1600) NOT NULL,
    symptom character varying(1200) NOT NULL,
    repair_strategy character varying(1200) NOT NULL,
    severity character varying(40) NOT NULL,
    primary_knowledge_node_code character varying(160) NOT NULL,
    knowledge_node_codes character varying(2400) NOT NULL,
    applicable_languages character varying(800) NOT NULL
) ON COMMIT DROP;

INSERT INTO discipline_quality_v4_mistakes (
    code, category, name, description, skill_unit_code, mistake_type,
    misconception, symptom, repair_strategy, severity,
    primary_knowledge_node_code, knowledge_node_codes, applicable_languages
) VALUES
    ('MP_DQ4_BRANCH_CONDITIONS_OVERLAP',
     '易错点/条件分支', '分支条件重叠且宽条件先吞掉窄情况',
     '多个条件覆盖同一输入，且更宽的条件排在前面，使后续更具体分支永远无法进入。',
     'SK_BRANCH_CASE_COVERAGE', 'LOGIC',
     '只逐条判断每个条件看起来是否合理，没有比较所有分支覆盖集合及其顺序。',
     '边界附近输入总进入同一分支，交换 if/else if 顺序后结果变化。',
     '把每个条件写成区间或集合，先消除重叠或按从具体到一般排序，再用交界值复测。',
     'HIGH', 'BASIC.BRANCH.CASE.覆盖所有情况',
     E'BASIC.BRANCH.CASE.覆盖所有情况\nBASIC.BRANCH.CASE.互斥条件\nBASIC.BRANCH.CASE.边界归属', E'PYTHON\nCPP17'),
    ('MP_DQ4_FUNCTION_MUTATES_HIDDEN_SHARED_STATE',
     '易错点/函数契约', '函数在返回值之外修改隐藏共享状态',
     '函数表面上返回计算结果，却同时修改全局变量或共享容器，使调用顺序改变后结果不同。',
     'SK_FUNCTION_CONTRACT', 'STATE',
     '认为只要函数有返回值，内部对外部对象的修改就不属于函数合同。',
     '同一函数重复调用、换序调用或在测试间复用时出现额外累计和状态污染。',
     '列出函数所有输入、返回和副作用；不需要共享的状态改为局部变量，需要修改的对象在合同中显式声明。',
     'HIGH', 'BASIC.FUNCTION.DEF.返回值设计',
     E'BASIC.FUNCTION.DEF.返回值设计\nBASIC.FUNCTION.DEF.参数设计\nBASIC.FUNCTION.RETURN.输出与返回分离\nBASIC.FUNCTION.PARAM.可变对象风险', E'PYTHON\nCPP17'),
    ('MP_DQ4_INDEX_CONVERSION_APPLIED_TWICE',
     '易错点/数组下标', '题面编号到数组下标的偏移被执行两次',
     '输入编号已转换为 0 基下标后，在访问或输出阶段再次减一，导致首元素变成负下标或整体左移。',
     'SK_INDEX_BASE_MAPPING', 'BOUNDARY',
     '没有规定系统内部统一使用哪种基准，每段代码都自行猜测是否需要转换。',
     '只有编号 1 或最后编号失败，中间位置结果整体偏移一位。',
     '入口只做一次外部到内部映射，内部统一使用 0 基；输出时按明确逆映射恢复并用首尾编号验证。',
     'HIGH', 'BASIC.ARRAY.INDEX.下标映射',
     E'BASIC.ARRAY.INDEX.下标映射\nBASIC.ARRAY.INDEX.0_基下标\nBASIC.ARRAY.INDEX.1_基下标', E'PYTHON\nCPP17'),
    ('MP_DQ4_SENTINEL_COLLIDES_WITH_VALID_RANGE',
     '易错点/整数范围', '哨兵值落在合法答案范围内',
     '用固定大数或小数表示无穷、未访问或不存在，但题目合法值可能达到同一范围，导致真实答案与哨兵无法区分。',
     'SK_INTEGER_RANGE_PROTECTION', 'VALUE_RANGE',
     '只凭习惯写 1e9 或 -1，没有先计算合法结果的上下界和后续运算。',
     '极值数据被误判为不可达，或 INF 加权后溢出并变成更优值。',
     '先估算合法范围，再选择有安全余量的宽类型哨兵；参与加法前先判断状态可达。',
     'HIGH', 'BASIC.TYPE.INTEGER.整型溢出',
     E'BASIC.TYPE.INTEGER.整型溢出\nBASIC.TYPE.INTEGER.int_范围\nBASIC.TYPE.INTEGER.long_long_范围', E'PYTHON\nCPP17'),
    ('MP_DQ4_INPUT_CURSOR_DRIFTS_FROM_TOKEN_SCHEMA',
     '易错点/输入结构', '读取游标与题面字段结构错位',
     '某个字段少读、多读或按错误类型读取后，后续 token 虽然仍可解析，却全部被赋给错误变量。',
     'SK_IO_STRUCTURE_MAPPING', 'IO_FORMAT',
     '把样例换行当成字段边界，没有把输入定义写成确定的字段数量和嵌套层级。',
     '第一组或前几个字段正确，随后值看似随机，常在混合整行字符串或多组数据时出现。',
     '先画输入 schema 和 token 游标；逐块断言已消费数量，混合读取时显式处理残留换行。',
     'HIGH', 'BASIC.IO.STDIN.输入顺序映射',
     E'BASIC.IO.STDIN.输入顺序映射\nBASIC.IO.STDIN.混合数字与字符串读取\nBASIC.IO.MULTI_CASE.显式_T_组循环\nCONTEST.READING.INPUT.数据组数', E'PYTHON\nCPP17'),
    ('MP_DQ4_GRID_ACCESS_BEFORE_BOUNDARY_CHECK',
     '易错点/二维数组', '方向移动后先访问网格再判界',
     '生成相邻坐标后立即读取数组或 visited，再检查行列范围，边缘位置会发生越界。',
     'SK_MATRIX_COORDINATE_MAPPING', 'BOUNDARY',
     '把边界判断当成是否继续搜索的条件，而不是任何数组访问的前置条件。',
     '中心位置正常，四条边或四个角出现运行错误、脏数据或不可复现结果。',
     '固定顺序为生成坐标、判行列范围、判障碍和访问状态、最后访问或入队；用 1×1 网格复测。',
     'HIGH', 'BASIC.ARRAY.MATRIX.边界检查',
     E'BASIC.ARRAY.MATRIX.边界检查\nBASIC.ARRAY.MATRIX.方向遍历\nBASIC.ARRAY.MATRIX.行列含义', E'PYTHON\nCPP17'),
    ('MP_DQ4_BACKTRACK_EARLY_RETURN_SKIPS_RESTORE',
     '易错点/递归回溯', '回溯分支提前返回时跳过状态恢复',
     '选择后进入递归，某个成功、失败或剪枝分支直接 return，未执行对应的 pop、取消标记或计数回退。',
     'SK_RECURSION_BACKTRACK_STATE', 'STATE',
     '只在函数末尾写统一恢复，没有检查所有提前返回路径是否经过该语句。',
     '第一个分支正确，后续兄弟分支继承路径或 visited，结果依赖搜索顺序。',
     '让 choose/recurse/undo 结构成对出现；提前返回前恢复全部共享状态，或用局部副本隔离分支。',
     'HIGH', 'BASIC.RECURSION.STATE.回溯恢复',
     E'BASIC.RECURSION.STATE.回溯恢复\nALGO.SEARCH.DFS.回溯恢复\nALGO.SEARCH.DFS.路径记录', E'PYTHON\nCPP17'),
    ('MP_DQ4_RECURSION_STATE_DOES_NOT_SHRINK',
     '易错点/递归搜索', '递归调用没有严格缩小问题规模',
     '递归参数在某条分支保持不变或循环回到旧状态，即使写了终止条件也永远无法到达。',
     'SK_RECURSION_BASE_PROGRESS', 'RUNTIME',
     '只检查是否存在 base case，没有证明每次调用都让某个度量单调接近出口。',
     '特定输入无限递归、重复状态快速膨胀或最终栈溢出。',
     '定义长度、剩余量、区间宽度或未访问数作为递归度量，并逐条验证每个调用都严格减少。',
     'HIGH', 'BASIC.RECURSION.STATE.规模缩小',
     E'BASIC.RECURSION.BASE.终止条件\nBASIC.RECURSION.STATE.规模缩小\nBASIC.RECURSION.STATE.递归转移', E'PYTHON\nCPP17'),
    ('MP_DQ4_SLICE_HALF_OPEN_ENDPOINT_MIXED',
     '易错点/字符串切片', '闭区间题意直接套入左闭右开切片',
     '题面要求包含右端点 r，却直接使用 s[l:r]，导致末字符丢失；或把 C++ 长度参数和 Python 结束位置混用。',
     'SK_STRING_SLICE_ENDPOINT', 'BOUNDARY',
     '没有先把外部区间统一为内部半开区间，再调用具体语言 API。',
     '长度为 1 的区间得到空串，或只在触及字符串末尾时数量差一。',
     '先写目标字符下标集合，再换算为 [l,r+1) 或 pos,len；用单字符和末尾区间验证。',
     'HIGH', 'BASIC.STRING.SUBSTRING.长度参数',
     E'BASIC.STRING.SUBSTRING.长度参数\nBASIC.STRING.SUBSTRING.起止位置\nBASIC.STRING.SUBSTRING.Python_CPP_切片差异', E'PYTHON\nCPP17'),
    ('MP_DQ4_MULTICASE_PARTIAL_BUFFER_RESET',
     '易错点/多组状态', '只按新一组规模清空导致旧尾部残留',
     '数组或邻接表上一组使用范围更大，新一组只清空当前 n 个位置，后续仍可能访问未清理的旧区域。',
     'SK_V7_MULTICASE_STATE_RESET_CONTRACT', 'STATE',
     '认为重置范围只需等于当前输入规模，没有记录容器历史已用范围和后续访问上界。',
     '先大后小的两组数据失败，单独运行任一组都正确。',
     '按历史最大已用范围清空、重建每组容器或使用版本戳；用大组后接最小组固定回归。',
     'HIGH', 'BASIC.IO.MULTI_CASE.每组状态重置',
     E'BASIC.IO.MULTI_CASE.每组状态重置\nCONTEST.SUBMIT.CHECKLIST.多组状态复查\nENG.ERROR.WRONG_ANSWER.状态未重置', E'PYTHON\nCPP17'),
    ('MP_DQ4_INTERMEDIATE_SUM_EXCEEDS_RESULT_RANGE',
     '易错点/数值边界', '中间累计量超范围后才缩放或取模',
     '公式最终会除法、平均或取模，但在此之前累计和或乘积已超出当前类型范围。',
     'SK_V7_NUMERIC_RANGE_PRECISION_CONTRACT', 'VALUE_RANGE',
     '只估算最终输出范围，没有沿表达式计算中间节点的最大绝对值。',
     '小数据正确，极值输入结果变负、跳变或不同优化级别结果不一致。',
     '画表达式范围树；在运算前提升类型，允许时分步取模或调整先除后乘顺序。',
     'HIGH', 'BASIC.TYPE.INTEGER.整型溢出',
     E'BASIC.TYPE.INTEGER.整型溢出\nBASIC.TYPE.INTEGER.long_long_范围\nMATH.NUMBER.MOD.大数防溢出', E'PYTHON\nCPP17'),
    ('MP_DQ4_OVERLAP_MATCH_ADVANCE_TOO_FAR',
     '易错点/字符串细节', '命中后推进整个模式长度漏掉重叠匹配',
     '找到一次模式后把起点直接增加模式长度，跳过了与当前命中共享字符的下一次合法出现。',
     'SK_V7_STRING_BOUNDARY_ENCODING_CONTRACT', 'BOUNDARY',
     '默认所有匹配都不重叠，没有先读取题目对出现次数的定义。',
     '普通字符串计数正确，但 aaa 中 aa、ababa 中 aba 等周期样例少计。',
     '先确认是否允许重叠；朴素匹配命中后起点加一，状态机按前后缀状态继续，并用周期串复测。',
     'HIGH', 'BASIC.STRING.MATCH.重叠匹配计数',
     E'BASIC.STRING.MATCH.统计出现次数\nBASIC.STRING.MATCH.重叠匹配计数\nBASIC.STRING.MATCH.查找出现位置', E'PYTHON\nCPP17'),
    ('MP_DQ4_CONSTRAINT_UNIT_OR_INCLUSIVE_BOUND_MISREAD',
     '易错点/读题建模', '约束单位或端点包含关系读错',
     '把 MB 当字节、毫秒当秒，或把“不超过”“至少”读成严格不等号，使资源估算和边界条件同时偏离题意。',
     'SK_READING_OBJECTIVE_CONSTRAINT', 'MODELING',
     '只抄约束数字，没有连同单位、等号和对象含义记录。',
     '算法选择看似匹配数量级，但最大边界、内存或时间判断仍系统性错误。',
     '建立“变量、上下界、是否含端点、单位、代码类型”五列表，并用恰等于边界的输入验证。',
     'HIGH', 'CONTEST.READING.CONSTRAINT.数据范围',
     E'CONTEST.READING.CONSTRAINT.数据范围\nCONTEST.READING.CONSTRAINT.内存限制\nBASIC.EXPR.COMPARE.边界比较', E'PYTHON\nCPP17'),
    ('MP_DQ4_SAMPLE_ASSUMPTION_NOT_IN_STATEMENT',
     '易错点/读题建模', '把样例中的偶然规律当成题目保证',
     '因为样例已排序、没有重复、图连通或答案唯一，就在算法中依赖这些性质，但题面并未给出对应保证。',
     'SK_READING_SAMPLE_CONSTRAINT_CROSSCHECK', 'MODELING',
     '把样例当成完整规格，没有区分“题面明确保证”和“样例恰好如此”。',
     '所有样例通过，加入乱序、重复、非连通或多解输入后立即失败。',
     '把每个算法假设逐条回指题面句子；找不到来源的假设必须删除或用反例测试。',
     'HIGH', 'CONTEST.READING.CONSTRAINT.隐藏特殊条件',
     E'CONTEST.READING.CONSTRAINT.隐藏特殊条件\nCONTEST.READING.INPUT.数组规模\nCONTEST.READING.OUTPUT.多答案要求\nCONTEST.SUBMIT.CHECKLIST.样例复测', E'PYTHON\nCPP17'),
    ('MP_DQ4_GCD_LOOP_DIVIDES_BY_ZERO_AFTER_SWAP',
     '易错点/数论边界', '欧几里得循环交换后仍执行对零取余',
     '循环条件或赋值顺序错误，使除数已经为 0 时仍计算 a%b。',
     'SK_NUMBER_THEORY_BOUNDARY', 'BOUNDARY',
     '背诵交换写法但没有维护“执行取余前 b 非零”的不变量。',
     '输入 0、相等数或某一步整除时发生运行错误。',
     '每轮先验证 b!=0，再同时更新 a=b、b=a%b；用 gcd(0,a)、gcd(a,0) 和整除对复测。',
     'HIGH', 'MATH.NUMBER.GCD.除零边界',
     E'MATH.NUMBER.GCD.除零边界\nMATH.NUMBER.GCD.欧几里得算法\nMATH.NUMBER.GCD.最小公倍数', E'PYTHON\nCPP17'),
    ('MP_DQ4_COUNT_MODEL_IGNORES_REPETITION_RULE',
     '易错点/数学计数', '排列组合模型忽略是否允许重复选择',
     '只根据“选 k 个”套用排列或组合公式，没有判断同一对象能否在多个位置重复出现。',
     'SK_V10_COUNTING_OBJECT_ORDER_CONTRACT', 'MODELING',
     '只区分顺序重要与否，却漏掉有放回、无放回这一独立维度。',
     '最小规模看似正确，k 大于对象数或出现重复元素时方案数偏差。',
     '先填写“对象是否可区分、顺序是否重要、是否允许重复”三维模型表，再选择公式或枚举。',
     'HIGH', 'MATH.COUNT.PERM.排列',
     E'MATH.COUNT.PERM.排列\nMATH.COUNT.PERM.组合\nMATH.COUNT.PERM.重复选择\nMATH.COUNT.PERM.除重', E'PYTHON\nCPP17'),
    ('MP_DQ4_SUBMASK_LOOP_STUCK_AT_ZERO',
     '易错点/位掩码', '子掩码枚举到零后回绕或死循环',
     '使用 sub=(sub-1)&mask 时没有在 sub=0 后退出；无符号减一回绕后又回到 mask。',
     'SK_V13_BITMASK_STATE_TRANSITION_CONTRACT', 'LOOP_BOUNDARY',
     '只记住转移表达式，没有定义空集是否处理和循环结束状态。',
     '非空子集都处理正确，但程序在最后不结束或重复整轮枚举。',
     '明确采用处理后判断或先判断循环；手算三位 mask 序列并单独处理空集。',
     'HIGH', 'MATH.BIT.MASK.枚举子集',
     E'MATH.BIT.MASK.枚举子集\nMATH.BIT.MASK.位数限制\nMATH.BIT.MASK.集合掩码', E'PYTHON\nCPP17'),
    ('MP_DQ4_MANHATTAN_DELTA_MISSING_ABSOLUTE_VALUE',
     '易错点/几何坐标距离', '曼哈顿距离的坐标差没有取绝对值',
     '直接计算 (x1-x2)+(y1-y2)，方向相反时正负抵消，结果可能为负或小于真实最少步数。',
     'SK_V13_GEOMETRY_COORD_DISTANCE_CONTRACT', 'FORMULA',
     '把坐标差当成长度，没有区分有向位移和无向距离。',
     '只在目标位于右上方向时正确，交换两点顺序后距离改变。',
     '分别计算两个轴的绝对差再相加，并用交换端点、负坐标和同轴点验证对称性。',
     'HIGH', 'MATH.GEOMETRY.DIST.曼哈顿距离',
     E'MATH.GEOMETRY.DIST.曼哈顿距离\nMATH.GEOMETRY.COORD.行列坐标\nMATH.GEOMETRY.RELATION.边界包含', E'PYTHON\nCPP17'),
    ('MP_DQ4_COUNTEREXAMPLE_CHANGES_MULTIPLE_FACTORS',
     '易错点/调试方法', '缩小反例时一次改变多个因素',
     '同时删除多段输入、改变数值和结构，失败消失后无法判断是哪一项与根因相关。',
     'SK_DEBUG_MINIMAL_COUNTEREXAMPLE', 'DEBUG_METHOD',
     '把反例缩小理解成任意重写，而不是保持失败的单变量实验。',
     '能得到更小输入，却无法稳定复现或解释第一次分叉。',
     '每次只删除一块或简化一个值，记录是否仍失败；保留最小失败版本并手算期望轨迹。',
     'MEDIUM', 'CONTEST.SUBMIT.REVIEW.最小反例构造',
     E'CONTEST.SUBMIT.REVIEW.最小反例构造\nENG.DEBUG.SAMPLE.最小反例\nENG.DEBUG.SAMPLE.手算对照', E'PYTHON\nCPP17'),
    ('MP_DQ4_COMPLEXITY_ESTIMATES_AVERAGE_NOT_MAXIMUM_SCALE',
     '易错点/复杂度空间', '只按平均规模估算而忽略最大组合约束',
     '用日常或样例规模估算性能，没有把 n、m、q、状态数和最坏结构同时代入。',
     'SK_V10_COMPLEXITY_SPACE_CONTRACT', 'COMPLEXITY',
     '认为平均运行得快就足以通过评测，忽略测试会专门覆盖约束上限。',
     '本地测试稳定，最大数据突然 TLE、MLE 或递归爆栈。',
     '建立最大规模资源表，分别计算操作数、元素字节、并存结构和最深调用链。',
     'HIGH', 'ENG.DEBUG.BOUNDARY.最大输入',
     E'ENG.DEBUG.BOUNDARY.最大输入\nENG.COMPLEXITY.TIME.数据范围反推\nENG.COMPLEXITY.SPACE.二维空间\nCONTEST.READING.CONSTRAINT.内存限制', E'PYTHON\nCPP17'),
    ('MP_DQ4_ERROR_CLASSIFIED_FROM_LAST_MESSAGE',
     '易错点/调试复盘', '根据最后一个表象而非第一条可靠信号分类',
     '忽略第一条编译错误、退出码或首个输出分叉，只按日志末尾或最终异常猜测错误类型。',
     'SK_V10_DEBUG_ERROR_TRIAGE_CONTRACT', 'DEBUG_METHOD',
     '认为所有日志同等重要，没有按因果顺序区分根信号和连锁症状。',
     '修复后错误类型不断变化，或围绕格式调整却真实问题是运行错误和状态污染。',
     '按编译、启动、退出状态、首个输出分叉、资源记录的顺序建立信号阶梯，从第一条异常向代码回溯。',
     'HIGH', 'CONTEST.SUBMIT.REVIEW.错误类型定位',
     E'CONTEST.SUBMIT.REVIEW.错误类型定位\nENG.ERROR.WRONG_ANSWER.样例通过隐藏失败\nENG.ERROR.RUNTIME.空容器访问保护\nENG.ERROR.TIME.复杂度过高', E'PYTHON\nCPP17'),
    ('MP_DQ4_FIX_PATCHES_SYMPTOM_WITHOUT_ROOT_CAUSE',
     '易错点/提交调试', '为失败样例加特判但未修复根因',
     '看到一个失败输入后增加针对常量、位置或样例形态的条件，使当前样例通过，却没有修复通用边界、状态或模型错误。',
     'SK_V6_DEBUG_SUBMISSION_FEEDBACK_LOOP', 'DEBUG_METHOD',
     '把“当前失败消失”当成修复完成，没有写出可证伪的根因假设。',
     '原失败样例通过，相邻边界、同类变式或随机对拍仍失败。',
     '先定位第一处分叉并写根因，再修改产生分叉的规则；至少复测原例、相邻边界和同类变式。',
     'HIGH', 'ENG.ERROR.WRONG_ANSWER.边界遗漏',
     E'ENG.ERROR.WRONG_ANSWER.边界遗漏\nENG.ERROR.WRONG_ANSWER.样例通过隐藏失败\nCONTEST.SUBMIT.CHECKLIST.边界复测\nCONTEST.SUBMIT.REVIEW.最小反例构造', E'PYTHON\nCPP17');

INSERT INTO public.ai_standard_mistake_points (
    applicable_languages, category, code, created_at, description, enabled,
    knowledge_node_codes, library_version, mistake_type, misconception, name,
    primary_knowledge_node_code, repair_strategy, severity, skill_unit_code,
    symptom, updated_at
)
SELECT v.applicable_languages, v.category, v.code, CURRENT_TIMESTAMP, v.description, true,
       v.knowledge_node_codes, 'informatics-discipline-quality-v4', v.mistake_type,
       v.misconception, v.name, v.primary_knowledge_node_code, v.repair_strategy,
       v.severity, v.skill_unit_code, v.symptom, CURRENT_TIMESTAMP
FROM discipline_quality_v4_mistakes v
JOIN public.ai_standard_skill_units s
  ON s.code = v.skill_unit_code
 AND s.enabled = true
 AND s.code NOT LIKE 'SK_COMPAT_%'
 AND s.description NOT LIKE '%用于兼容旧 AI 标准库标签%'
JOIN public.informatics_knowledge_nodes n
  ON n.code = v.primary_knowledge_node_code AND n.enabled = true AND n.type = 'KNOWLEDGE_POINT'
WHERE NOT EXISTS (
    SELECT 1
    FROM regexp_split_to_table(v.knowledge_node_codes, E'\n') ref
    LEFT JOIN public.informatics_knowledge_nodes kn
      ON kn.code = btrim(ref) AND kn.enabled = true
    WHERE btrim(ref) <> '' AND kn.code IS NULL
)
ON CONFLICT (code) DO UPDATE SET
    applicable_languages = EXCLUDED.applicable_languages,
    category = EXCLUDED.category,
    description = EXCLUDED.description,
    enabled = EXCLUDED.enabled,
    knowledge_node_codes = EXCLUDED.knowledge_node_codes,
    library_version = EXCLUDED.library_version,
    mistake_type = EXCLUDED.mistake_type,
    misconception = EXCLUDED.misconception,
    name = EXCLUDED.name,
    primary_knowledge_node_code = EXCLUDED.primary_knowledge_node_code,
    repair_strategy = EXCLUDED.repair_strategy,
    severity = EXCLUDED.severity,
    skill_unit_code = EXCLUDED.skill_unit_code,
    symptom = EXCLUDED.symptom,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO public.ai_standard_library_items (
    applicable_languages, category, code, common_code_patterns, common_misconception,
    created_at, description, enabled, hintl1, hintl2, hintl3, knowledge_node_codes,
    layer, library_version, mistake_type, name, primary_knowledge_node_code,
    related_knowledge_node_codes, required_evidence, severity, skill_unit_code,
    student_benefit, student_explanation, teacher_explanation, teaching_action,
    updated_at, when_to_use
)
SELECT v.applicable_languages, v.category, v.code, v.symptom, v.misconception,
       CURRENT_TIMESTAMP, v.description, true, v.misconception, v.symptom,
       v.repair_strategy, v.knowledge_node_codes, 'MISTAKE_POINT',
       'informatics-discipline-quality-v4', v.mistake_type, v.name,
       v.primary_knowledge_node_code, v.knowledge_node_codes,
       '当前提交代码、判题结果或可复现状态轨迹', v.severity, v.skill_unit_code,
       v.symptom, v.misconception, v.repair_strategy,
       '定位首个偏差并按修复策略完成相邻边界复测', CURRENT_TIMESTAMP, v.description
FROM discipline_quality_v4_mistakes v
JOIN public.ai_standard_mistake_points m
  ON m.code = v.code AND m.enabled = true
ON CONFLICT (layer, code) DO UPDATE SET
    applicable_languages = EXCLUDED.applicable_languages,
    category = EXCLUDED.category,
    common_code_patterns = EXCLUDED.common_code_patterns,
    common_misconception = EXCLUDED.common_misconception,
    description = EXCLUDED.description,
    enabled = EXCLUDED.enabled,
    hintl1 = EXCLUDED.hintl1,
    hintl2 = EXCLUDED.hintl2,
    hintl3 = EXCLUDED.hintl3,
    knowledge_node_codes = EXCLUDED.knowledge_node_codes,
    library_version = EXCLUDED.library_version,
    mistake_type = EXCLUDED.mistake_type,
    name = EXCLUDED.name,
    primary_knowledge_node_code = EXCLUDED.primary_knowledge_node_code,
    related_knowledge_node_codes = EXCLUDED.related_knowledge_node_codes,
    required_evidence = EXCLUDED.required_evidence,
    severity = EXCLUDED.severity,
    skill_unit_code = EXCLUDED.skill_unit_code,
    student_benefit = EXCLUDED.student_benefit,
    student_explanation = EXCLUDED.student_explanation,
    teacher_explanation = EXCLUDED.teacher_explanation,
    teaching_action = EXCLUDED.teaching_action,
    updated_at = CURRENT_TIMESTAMP,
    when_to_use = EXCLUDED.when_to_use;

INSERT INTO public.ai_standard_library_legacy_mappings (
    confidence, created_at, legacy_code, legacy_layer, migration_status,
    source_version, target_code, target_type, updated_at
)
SELECT 1.0, CURRENT_TIMESTAMP, v.code, 'MISTAKE_POINT', 'MAPPED',
       'informatics-discipline-quality-v4', v.code, 'MISTAKE_POINT', CURRENT_TIMESTAMP
FROM discipline_quality_v4_mistakes v
JOIN public.ai_standard_mistake_points m
  ON m.code = v.code AND m.enabled = true
ON CONFLICT (legacy_layer, legacy_code) DO UPDATE SET
    confidence = EXCLUDED.confidence,
    migration_status = EXCLUDED.migration_status,
    source_version = EXCLUDED.source_version,
    target_code = EXCLUDED.target_code,
    target_type = EXCLUDED.target_type,
    updated_at = CURRENT_TIMESTAMP;

DO $$
DECLARE
    domain_code text;
BEGIN
    IF EXISTS (SELECT 1 FROM public.informatics_knowledge_nodes) THEN
        IF (SELECT count(*) FROM public.informatics_knowledge_nodes
            WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
              AND library_version = 'informatics-knowledge-discipline-v4'
              AND description NOT LIKE '细颗粒知识点：%') <> 48 THEN
            RAISE EXCEPTION 'V6 expected exactly 48 curated knowledge points';
        END IF;

        FOREACH domain_code IN ARRAY ARRAY['BASIC', 'MATH', 'ENG', 'CONTEST'] LOOP
            IF (SELECT count(*) FROM public.informatics_knowledge_nodes
                WHERE enabled = true AND type = 'KNOWLEDGE_POINT'
                  AND library_version = 'informatics-knowledge-discipline-v4'
                  AND code LIKE domain_code || '.%') <> 12 THEN
                RAISE EXCEPTION 'V6 expected exactly 12 curated knowledge points in %', domain_code;
            END IF;
        END LOOP;

        IF (SELECT count(*)
            FROM public.informatics_discipline_scope_mappings m
            JOIN public.informatics_knowledge_nodes n ON n.code = m.knowledge_node_code
            WHERE m.enabled = true AND n.type = 'CHAPTER'
              AND ((m.framework_code = 'MOE_HIGH_SCHOOL_IT_2020'
                    AND m.source_reference LIKE 'https://jyj.changdu.gov.cn/%')
                OR (m.framework_code = 'CCF_NOI_2025'
                    AND m.source_reference = 'https://www.noi.cn/upload/resources/file/2025/04/18/NOI_Syllabus_Edition_2025.pdf'))) <> 26 THEN
            RAISE EXCEPTION 'V6 expected exactly 26 chapter-level framework mappings';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.informatics_knowledge_nodes n
            CROSS JOIN LATERAL regexp_split_to_table(COALESCE(n.prerequisites, ''), E'\n') ref
            LEFT JOIN public.informatics_knowledge_nodes target ON target.code = btrim(ref)
            WHERE n.library_version = 'informatics-knowledge-discipline-v4'
              AND btrim(ref) <> '' AND target.code IS NULL
        ) THEN
            RAISE EXCEPTION 'V6 curated knowledge contains invalid prerequisite code';
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM public.ai_standard_skill_units) THEN
        IF (SELECT count(*) FROM public.ai_standard_mistake_points
            WHERE enabled = true AND library_version = 'informatics-discipline-quality-v4') <> 22 THEN
            RAISE EXCEPTION 'V6 expected exactly 22 normalized mistake points';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.ai_standard_mistake_points m
            JOIN public.ai_standard_skill_units s ON s.code = m.skill_unit_code
            WHERE m.enabled = true
              AND m.library_version = 'informatics-discipline-quality-v4'
              AND (s.code LIKE 'SK_COMPAT_%'
                OR s.description LIKE '%用于兼容旧 AI 标准库标签%')
        ) THEN
            RAISE EXCEPTION 'V6 formal mistake points must not target compatibility skills';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.ai_standard_mistake_points m
            LEFT JOIN public.ai_standard_library_items item
              ON item.layer = 'MISTAKE_POINT' AND item.code = m.code
            LEFT JOIN public.ai_standard_library_legacy_mappings map
              ON map.legacy_layer = 'MISTAKE_POINT' AND map.legacy_code = m.code
            WHERE m.enabled = true
              AND m.library_version = 'informatics-discipline-quality-v4'
              AND (item.id IS NULL OR item.enabled IS DISTINCT FROM true
                OR item.skill_unit_code IS DISTINCT FROM m.skill_unit_code
                OR item.primary_knowledge_node_code IS DISTINCT FROM m.primary_knowledge_node_code
                OR item.related_knowledge_node_codes IS DISTINCT FROM m.knowledge_node_codes
                OR map.id IS NULL OR map.migration_status <> 'MAPPED'
                OR map.target_type <> 'MISTAKE_POINT' OR map.target_code <> m.code)
        ) THEN
            RAISE EXCEPTION 'V6 mistake normalized, snapshot or mapping structures are inconsistent';
        END IF;
    END IF;
END $$;

CREATE TEMP TABLE discipline_quality_v4_improvements (
    code character varying(100) PRIMARY KEY,
    category character varying(80) NOT NULL,
    name character varying(120) NOT NULL,
    description character varying(800) NOT NULL,
    skill_unit_code character varying(160) NOT NULL,
    primary_knowledge_node_code character varying(160) NOT NULL,
    knowledge_node_codes character varying(2400) NOT NULL,
    related_mistake_codes character varying(1600) NOT NULL,
    improvement_goal character varying(1200) NOT NULL,
    practice_strategy character varying(1200) NOT NULL,
    student_benefit character varying(800) NOT NULL,
    teacher_explanation character varying(1200) NOT NULL,
    teaching_action character varying(120) NOT NULL,
    applicable_languages character varying(800) NOT NULL
) ON COMMIT DROP;

INSERT INTO discipline_quality_v4_improvements (
    code, category, name, description, skill_unit_code,
    primary_knowledge_node_code, knowledge_node_codes, related_mistake_codes,
    improvement_goal, practice_strategy, student_benefit, teacher_explanation,
    teaching_action, applicable_languages
) VALUES
    ('IP_DQ4_BRANCH_COVERAGE_PARTITION_TABLE',
     '提升点/条件分支', '用集合分区表验证分支完备性',
     '适用于分支能运行，但等号归属、条件重叠和默认分支仍依赖试错的学生。',
     'SK_BRANCH_CASE_COVERAGE', 'BASIC.BRANCH.CASE.覆盖所有情况',
     E'BASIC.BRANCH.CASE.覆盖所有情况\nBASIC.BRANCH.CASE.互斥条件\nBASIC.BRANCH.CASE.边界归属',
     E'MP_DQ4_BRANCH_CONDITIONS_OVERLAP\nMP_BRANCH_DEFAULT_CASE_MISSING\nMP_BRANCH_EQUAL_CASE_FALLS_WRONG_SIDE',
     '让所有合法输入恰好属于一个分支，并能从集合覆盖直接解释条件顺序。',
     '把输入全集写在表头，每个分支填写条件、覆盖区间、是否含端点和与前序分支的交集；用每个交界点的前一值、等值、后一值逐项复测。',
     '减少靠交换 if 顺序碰答案，能在编码前发现遗漏和重叠。',
     '随机给出一个边界值，要求学生先指出它属于哪个集合，再说明实际代码会进入哪个分支；两者不一致即回到分区表修正。',
     '填写分支分区表并复测交界值', E'PYTHON\nCPP17'),
    ('IP_DQ4_FUNCTION_INPUT_OUTPUT_EFFECT_CONTRACT',
     '提升点/函数契约', '建立函数输入返回副作用合同',
     '适用于函数参数、返回值、打印和共享状态混在一起，调用顺序改变就失效的学生。',
     'SK_FUNCTION_CONTRACT', 'BASIC.FUNCTION.DEF.返回值设计',
     E'BASIC.FUNCTION.DEF.参数设计\nBASIC.FUNCTION.DEF.返回值设计\nBASIC.FUNCTION.RETURN.输出与返回分离\nBASIC.FUNCTION.PARAM.可变对象风险',
     E'MP_DQ4_FUNCTION_MUTATES_HIDDEN_SHARED_STATE\nMP_FUNCTION_OUTPUT_RETURN_MIXED\nMP_FUNCTION_PARAMETER_ORDER_MISMATCH',
     '把函数的显式输入、返回、允许副作用和失败表示固定成可测试合同。',
     '选择三个函数，分别填写“参数名与含义、读取的外部状态、修改的外部状态、返回值、打印内容、失败表示”；然后交换两次调用顺序并重复调用，验证结果不受隐藏状态影响。',
     '函数可以被独立测试和复用，减少输出正确但调用方拿不到结果或状态被污染。',
     '教师只看合同随机构造一次调用，要求学生预测返回值和所有状态变化；任何未写入合同的变化都判为失败。',
     '填写函数合同并做换序调用测试', E'PYTHON\nCPP17'),
    ('IP_DQ4_INDEX_MAPPING_ROUND_TRIP',
     '提升点/数组下标', '用往返映射表统一编号与下标',
     '适用于知道 0 基和 1 基差异，却会在不同代码段重复偏移或忘记逆映射的学生。',
     'SK_INDEX_BASE_MAPPING', 'BASIC.ARRAY.INDEX.下标映射',
     E'BASIC.ARRAY.INDEX.0_基下标\nBASIC.ARRAY.INDEX.1_基下标\nBASIC.ARRAY.INDEX.下标映射\nBASIC.ARRAY.INDEX.越界访问',
     E'MP_DQ4_INDEX_CONVERSION_APPLIED_TWICE\nMP_ARRAY_ZERO_ONE_BASE_MIXED\nMP_ARRAY_ACCESS_N_AS_LAST_INDEX',
     '让外部编号到内部下标只转换一次，并能通过逆映射恢复原编号。',
     '为首个、中间和末尾对象填写“题面编号、入口转换、内部下标、合法范围、输出逆转换”五列表；验证 external→internal→external 后值不变。',
     '消除跨模块的差一位错误，首尾对象与输出编号更稳定。',
     '抽查任意一个数组访问，要求学生指出变量当前处于外部编号还是内部下标；含义不明确的变量必须重命名或回到入口统一转换。',
     '填写编号往返表并验证首尾对象', E'PYTHON\nCPP17'),
    ('IP_DQ4_INTEGER_RANGE_SENTINEL_LEDGER',
     '提升点/整数范围', '建立中间值与哨兵范围账本',
     '适用于最终类型看似足够，却在乘法、累计或 INF 运算中提前溢出的学生。',
     'SK_INTEGER_RANGE_PROTECTION', 'BASIC.TYPE.INTEGER.整型溢出',
     E'BASIC.TYPE.INTEGER.int_范围\nBASIC.TYPE.INTEGER.long_long_范围\nBASIC.TYPE.INTEGER.整型溢出\nMATH.NUMBER.MOD.大数防溢出',
     E'MP_DQ4_SENTINEL_COLLIDES_WITH_VALID_RANGE\nMP_INTEGER_MULTIPLY_OVERFLOW_BEFORE_CAST\nMP_INTEGER_DIVISION_TRUNCATES_RESULT',
     '为每个高风险表达式选择足够宽的操作数类型，并保证哨兵不与合法值冲突。',
     '从约束推导单值、乘积、累计和、距离与方案数上界；填写“表达式、最大绝对值、执行类型、接收类型、哨兵、是否参与加法”表，再用极值生成器复测。',
     '能在写代码前发现中间溢出和哨兵碰撞，而不是等大样例出现随机负数。',
     '教师随机选择一条表达式，学生必须先给数量级和执行类型再计算；只回答最终变量类型不算通过。',
     '填写范围账本并运行极值测试', E'PYTHON\nCPP17'),
    ('IP_DQ4_INPUT_SCHEMA_CURSOR_TRACE',
     '提升点/输入结构', '用输入 schema 追踪读取游标',
     '适用于样例能读入，但多组、混合字符串或嵌套字段让后续变量整体错位的学生。',
     'SK_IO_STRUCTURE_MAPPING', 'BASIC.IO.STDIN.输入顺序映射',
     E'BASIC.IO.STDIN.输入顺序映射\nBASIC.IO.STDIN.混合数字与字符串读取\nBASIC.IO.MULTI_CASE.显式_T_组循环\nCONTEST.READING.INPUT.数据组数',
     E'MP_DQ4_INPUT_CURSOR_DRIFTS_FROM_TOKEN_SCHEMA\nMP_IO_ONLY_READS_ONE_CASE\nMP_IO_DEBUG_OUTPUT_LEFT',
     '让每次读取都能对应题面字段和预期 token 数，读取结束时游标与 schema 同步。',
     '把输入说明展开为树状 schema，给每个节点标字段类型、重复次数和读取方式；对两组刻意不同的数据逐步记录读前游标、读入值和读后游标。',
     '能快速定位少读、多读和整行/token 混用，而不是在后续业务逻辑里追随机值。',
     '隐藏字段名只给输入流，要求学生按 schema 逐个消费并在每个块结束报告已读数量；数量不一致不得进入算法阶段。',
     '画输入结构并逐字段推进游标', E'PYTHON\nCPP17'),
    ('IP_DQ4_GRID_NEIGHBOR_GUARD_SEQUENCE',
     '提升点/二维数组', '固定网格邻居访问保护顺序',
     '适用于方向数组会写，但行列互换、漏方向或先访问后判界仍反复出现的学生。',
     'SK_MATRIX_COORDINATE_MAPPING', 'BASIC.ARRAY.MATRIX.边界检查',
     E'BASIC.ARRAY.MATRIX.行列含义\nBASIC.ARRAY.MATRIX.方向遍历\nBASIC.ARRAY.MATRIX.边界检查\nMATH.GEOMETRY.COORD.行列坐标',
     E'MP_DQ4_GRID_ACCESS_BEFORE_BOUNDARY_CHECK\nMP_MATRIX_DIRECTION_BOUNDARY_CHECK_INCOMPLETE\nMP_MATRIX_ROW_COL_SWAPPED',
     '让所有邻居访问遵循“生成、判界、判障碍、判状态、访问”的同一安全合同。',
     '在 1×1、1×4、3×5 网格的角、边、中心各选一点，逐方向填写候选坐标和五阶段结果；任何数组读取都必须出现在判界之后。',
     '非方阵和边缘位置不再触发越界，方向语义能被直接回放。',
     '教师随机改变 rows/cols 或方向顺序，要求学生不运行代码就判断每个候选在哪一阶段被拒绝。',
     '回放邻居五阶段保护序列', E'PYTHON\nCPP17'),
    ('IP_DQ4_BACKTRACK_SCOPE_GUARD',
     '提升点/递归回溯', '用作用域清单保护回溯恢复',
     '适用于正常返回能恢复，但成功返回、剪枝或异常分支会留下共享状态的学生。',
     'SK_RECURSION_BACKTRACK_STATE', 'BASIC.RECURSION.STATE.回溯恢复',
     E'BASIC.RECURSION.STATE.回溯恢复\nALGO.SEARCH.DFS.回溯恢复\nALGO.SEARCH.DFS.路径记录\nBASIC.RECURSION.STATE.递归转移',
     E'MP_DQ4_BACKTRACK_EARLY_RETURN_SKIPS_RESTORE\nMP_BACKTRACK_STATE_NOT_RESTORED\nMP_RECURSION_RESULT_IGNORED',
     '保证每个递归分支离开时共享状态与进入前一致，返回结果仍能向上传递。',
     '给路径、visited、计数和候选容器各写进入值、选择修改、恢复动作；枚举正常、剪枝、找到答案三种返回路径，逐项打勾确认恢复。',
     '搜索结果不再依赖分支顺序，提前返回也不会污染兄弟分支。',
     '随机暂停在任一 return 前，要求学生对比进入快照和当前共享状态；存在差异则必须先恢复再返回。',
     '检查每条返回路径的状态恢复', E'PYTHON\nCPP17'),
    ('IP_DQ4_RECURSION_DECREASING_MEASURE',
     '提升点/递归搜索', '为递归定义严格递减度量',
     '适用于写了终止条件，却仍有分支不推进、重复状态或爆栈的学生。',
     'SK_RECURSION_BASE_PROGRESS', 'BASIC.RECURSION.STATE.规模缩小',
     E'BASIC.RECURSION.BASE.终止条件\nBASIC.RECURSION.STATE.规模缩小\nBASIC.RECURSION.STATE.递归转移',
     E'MP_DQ4_RECURSION_STATE_DOES_NOT_SHRINK\nMP_RECURSION_BASE_CONDITION_MISSING',
     '让每条递归调用都严格减少一个有下界的度量，从而证明最终到达出口。',
     '为长度、区间宽度、剩余元素或未访问状态选择一个度量；在调用图前四层记录度量值，并逐分支证明新值小于旧值且不小于下界。',
     '能从递归参数判断是否终止，减少只靠运行后栈溢出发现问题。',
     '教师修改一个递归参数，要求学生立即判断度量是否仍递减；无法给出不等式则不能通过。',
     '记录递归度量并证明逐层缩小', E'PYTHON\nCPP17'),
    ('IP_DQ4_SLICE_INTERVAL_CONVERSION_CARD',
     '提升点/字符串', '建立闭区间与切片参数转换卡',
     '适用于子串逻辑正确，但不同语言 API 的 end、length 和半开区间反复混淆的学生。',
     'SK_STRING_SLICE_ENDPOINT', 'BASIC.STRING.SUBSTRING.长度参数',
     E'BASIC.STRING.SUBSTRING.起止位置\nBASIC.STRING.SUBSTRING.长度参数\nBASIC.STRING.SUBSTRING.Python_CPP_切片差异\nBASIC.STRING.SUBSTRING.空串处理',
     E'MP_DQ4_SLICE_HALF_OPEN_ENDPOINT_MIXED\nMP_STRING_LENGTH_USED_AS_END_INDEX\nMP_STRING_EMPTY_SUBSTRING_MISSED',
     '把题面下标集合稳定转换为 Python 半开区间或 C++ 起点加长度参数。',
     '用首字符、单字符、中段、到末尾和空结果五个区间，填写题面闭区间、内部半开区间、Python 参数、C++ 参数和期望长度。',
     '跨语言迁移时仍能从定义推导参数，不再靠给 r 加一反复试错。',
     '教师随机给 l、r 和字符串长度，学生必须先写期望字符下标集合，再写 API 参数。',
     '填写切片转换卡并核对长度', E'PYTHON\nCPP17'),
    ('IP_DQ4_MULTICASE_RESET_OWNERSHIP_TABLE',
     '提升点/多组状态细节', '建立多组状态所有权与清空范围表',
     '适用于简单多组输入能过，但先大后小、图结构或共享预处理场景发生串组的学生。',
     'SK_V7_MULTICASE_STATE_RESET_CONTRACT', 'BASIC.IO.MULTI_CASE.每组状态重置',
     E'BASIC.IO.MULTI_CASE.每组状态重置\nBASIC.IO.MULTI_CASE.显式_T_组循环\nCONTEST.SUBMIT.CHECKLIST.多组状态复查\nENG.ERROR.WRONG_ANSWER.状态未重置',
     E'MP_DQ4_MULTICASE_PARTIAL_BUFFER_RESET\nMP_V7_MULTICASE_ACCUMULATOR_NOT_RESET\nMP_V7_MULTICASE_GRAPH_NOT_CLEARED_BETWEEN_CASES\nMP_V7_MULTICASE_OUTPUT_CASE_NUMBER_OFF_BY_ONE',
     '让每个状态有明确生命周期、清空位置和完整范围，同时保留可共享只读预处理。',
     '为数组、容器、累计值、visited 和预处理表填写“所有者、创建层级、最大已用范围、重置动作、是否跨组共享”；固定运行大组→小组→大组回归。',
     '能消除部分清空和错误共享，又避免重复构建真正可复用的预处理。',
     '教师指定一个变量，学生必须说出它属于程序、测试组还是循环迭代；回答不唯一说明所有权仍不清楚。',
     '填写状态所有权表并跑先大后小测试', E'PYTHON\nCPP17'),
    ('IP_DQ4_NUMERIC_EXPRESSION_RANGE_TREE',
     '提升点/数值边界细节', '画表达式范围树检查中间量',
     '适用于只看最终答案范围，忽略乘法、累计、取绝对值和取模前中间值的学生。',
     'SK_V7_NUMERIC_RANGE_PRECISION_CONTRACT', 'BASIC.TYPE.INTEGER.整型溢出',
     E'BASIC.TYPE.INTEGER.整型溢出\nBASIC.TYPE.INTEGER.long_long_范围\nBASIC.EXPR.ARITH.整数除法\nMATH.NUMBER.MOD.大数防溢出',
     E'MP_DQ4_INTERMEDIATE_SUM_EXCEEDS_RESULT_RANGE\nMP_V7_LONG_LONG_CAST_AFTER_MULTIPLICATION\nMP_V7_INTEGER_DIVISION_USED_FOR_RATIO\nMP_V7_FLOAT_EPS_COMPARISON_DIRECTION_WRONG',
     '让表达式每个内部节点都在执行类型范围内，并保持除法、取模和精度语义。',
     '选择三条高风险表达式画语法树；自底向上填写输入上界、节点运算、最大绝对值和执行类型，比较强制转换在运算前后的位置差异。',
     '可以解释溢出发生在哪个中间节点，而不是盲目把所有变量改成更宽类型。',
     '教师遮住最终赋值类型，只给表达式和约束，要求学生逐节点判定安全性。',
     '绘制表达式范围树并前置类型提升', E'PYTHON\nCPP17'),
    ('IP_DQ4_OVERLAPPING_MATCH_POINTER_TRACE',
     '提升点/字符串细节', '追踪重叠匹配的起点推进',
     '适用于普通匹配能统计，但周期字符串和重叠出现总少计的学生。',
     'SK_V7_STRING_BOUNDARY_ENCODING_CONTRACT', 'BASIC.STRING.MATCH.重叠匹配计数',
     E'BASIC.STRING.MATCH.统计出现次数\nBASIC.STRING.MATCH.重叠匹配计数\nBASIC.STRING.MATCH.查找出现位置\nBASIC.STRING.SUBSTRING.长度参数',
     E'MP_DQ4_OVERLAP_MATCH_ADVANCE_TOO_FAR\nMP_V7_STRING_OVERLAPPING_MATCH_SKIPPED\nMP_V7_CHAR_DIGIT_USED_AS_NUMERIC_VALUE',
     '根据题目是否允许重叠，精确维护每次命中后的下一搜索起点或前后缀状态。',
     '对 aaa/aa、ababa/aba 和无匹配三组输入，逐行记录起点、比较区间、是否命中、下一起点；分别执行允许重叠与不允许重叠规则。',
     '能从定义控制搜索推进，周期模式不再漏计或重复计数。',
     '教师随机改变模式长度或重叠规则，学生必须在不改其余代码前先更新“下一起点”合同。',
     '逐次记录匹配区间与下一起点', E'PYTHON\nCPP17'),
    ('IP_DQ4_CONSTRAINT_UNIT_BOUND_TABLE',
     '提升点/读题建模', '建立约束单位与端点预算表',
     '适用于能找到约束数字，却会忽略单位、等号和多个变量联合上界的学生。',
     'SK_READING_OBJECTIVE_CONSTRAINT', 'CONTEST.READING.CONSTRAINT.数据范围',
     E'CONTEST.READING.CONSTRAINT.数据范围\nCONTEST.READING.CONSTRAINT.内存限制\nCONTEST.READING.CONSTRAINT.隐藏特殊条件\nBASIC.EXPR.COMPARE.边界比较',
     E'MP_DQ4_CONSTRAINT_UNIT_OR_INCLUSIVE_BOUND_MISREAD\nMP_READING_HIDDEN_CONSTRAINT_IGNORED\nMP_READING_OBJECTIVE_MISIDENTIFIED',
     '把题面所有上下界转换成带单位、含端点和代码类型的资源合同。',
     '逐项填写“变量、对象、最小、最大、是否含端点、单位、联合出现条件、代码类型”；再将 MB、秒和数值范围换算为元素数和操作量。',
     '算法和类型选择建立在完整约束上，最大边界不再因单位或等号误读失效。',
     '教师删除表中任意一列，要求学生指出会产生哪类错误；不能说明风险则继续补表。',
     '填写约束预算表并换算资源单位', E'PYTHON\nCPP17'),
    ('IP_DQ4_STATEMENT_SAMPLE_EVIDENCE_MATRIX',
     '提升点/读题建模', '分离题面保证与样例观察',
     '适用于把样例已排序、无重复、连通或唯一解当成正式保证的学生。',
     'SK_READING_SAMPLE_CONSTRAINT_CROSSCHECK', 'CONTEST.READING.CONSTRAINT.隐藏特殊条件',
     E'CONTEST.READING.CONSTRAINT.隐藏特殊条件\nCONTEST.READING.INPUT.数组规模\nCONTEST.READING.OUTPUT.多答案要求\nCONTEST.SUBMIT.CHECKLIST.样例复测',
     E'MP_DQ4_SAMPLE_ASSUMPTION_NOT_IN_STATEMENT\nMP_READING_CONSTRAINT_ALGORITHM_MISMATCH\nMP_READING_SAMPLE_SHAPE_OVERFITS_FORMAT\nMP_READING_TIE_RULE_IGNORED',
     '让算法依赖的每个性质都能回指题面原句，样例只用于解释而不新增保证。',
     '建立“算法假设、题面证据、样例观察、无该保证的反例、是否必须处理”矩阵；为找不到题面证据的每项构造一个反样例。',
     '减少样例过拟合，能主动覆盖乱序、重复、非连通、并列和多解输入。',
     '教师给一个样例中的显著规律，学生必须在题面找到证据或立即构造违反该规律的合法输入。',
     '填写题面与样例证据矩阵', E'PYTHON\nCPP17'),
    ('IP_DQ4_GCD_ZERO_SIGN_TRACE',
     '提升点/数论边界', '追踪 gcd 的零值、符号和除数不变量',
     '作为已有数论特殊值表的专项深化，聚焦欧几里得循环中每一步除数非零和符号规范化。',
     'SK_NUMBER_THEORY_BOUNDARY', 'MATH.NUMBER.GCD.除零边界',
     E'MATH.NUMBER.GCD.除零边界\nMATH.NUMBER.GCD.欧几里得算法\nMATH.NUMBER.GCD.最小公倍数',
     E'MP_DQ4_GCD_LOOP_DIVIDES_BY_ZERO_AFTER_SWAP\nMP_LCM_OVERFLOW_BEFORE_DIVIDE\nMP_PRIME_ONE_TREATED_AS_PRIME',
     '让 gcd/lcm 在 0、负数、相等数和大数下保持定义与运算安全。',
     '对 (0,12)、(12,0)、(-18,24)、(21,21) 填写每轮 a、b、a%b 和循环条件；再用 gcd 推导 lcm，比较先除后乘的范围。',
     '能从循环不变量处理数论特殊值，而不是为某个输入添加零散特判。',
     '每轮取余前要求学生明确说出除数及非零证据；lcm 场景还需给出中间乘积上界。',
     '手算 gcd 轨迹并验证 lcm 范围', E'PYTHON\nCPP17'),
    ('IP_DQ4_COUNTING_MODEL_DECISION_CUBE',
     '提升点/数学计数', '用三维决策表选择计数模型',
     '作为已有计数对象卡的专项深化，增加对象可区分性、顺序和重复选择三个独立维度。',
     'SK_V10_COUNTING_OBJECT_ORDER_CONTRACT', 'MATH.COUNT.PERM.排列',
     E'MATH.COUNT.PERM.排列\nMATH.COUNT.PERM.组合\nMATH.COUNT.PERM.重复选择\nMATH.COUNT.PERM.除重\nMATH.COUNT.INCLUSION.二集合容斥',
     E'MP_DQ4_COUNT_MODEL_IGNORES_REPETITION_RULE\nMP_V10_COUNT_DUPLICATE_VALUES_OVERCOUNTED\nMP_V10_COUNT_ORDER_IMPORTANCE_MISMODELED\nMP_V10_INCLUSION_EXCLUSION_OVERLAP_OMITTED',
     '在套公式或写枚举前，先确定对象、顺序、重复和交集的完整模型。',
     '为六个相近题目填写“对象是否可区分、交换顺序是否新方案、是否允许重复、是否有重叠条件”；用 n≤5 全枚举生成方案集合校验公式。',
     '能区分排列、组合、可重选择、重复对象除重和容斥，模型错误在编码前暴露。',
     '随机改变一个维度，例如从无放回改为有放回，要求学生立即更新方案集合和计数方法。',
     '填写计数决策表并用小规模枚举对照', E'PYTHON\nCPP17'),
    ('IP_DQ4_SUBMASK_TERMINATION_TRACE',
     '提升点/位运算状态压缩', '手算子掩码枚举与终止轨迹',
     '作为已有位映射卡的专项深化，聚焦枚举顺序、空集处理和零值终止。',
     'SK_V13_BITMASK_STATE_TRANSITION_CONTRACT', 'MATH.BIT.MASK.枚举子集',
     E'MATH.BIT.MASK.集合掩码\nMATH.BIT.MASK.枚举子集\nMATH.BIT.MASK.位数限制\nMATH.BIT.MASK.掩码转移',
     E'MP_DQ4_SUBMASK_LOOP_STUCK_AT_ZERO\nMP_V13_SUBSET_ENUMERATION_MISSES_EMPTY_OR_FULL_SET\nMP_V13_BITMASK_SHIFT_USES_ONE_BASED_INDEX\nMP_V13_BITWISE_PRECEDENCE_CHANGES_MASK_TEST',
     '完整枚举目标 mask 的每个子集恰好一次，并明确是否处理空集。',
     '选择 mask=1101，逐轮写出 sub、sub-1、与 mask 后结果和是否终止；分别实现包含空集与不含空集版本，并核对数量为 2^popcount(mask)。',
     '能发现零值回绕、空集遗漏和位编号偏移，子集 DP 循环边界更稳定。',
     '教师随机给一个三到四位 mask，学生必须在运行前列出完整子集序列和预期数量。',
     '列出子掩码序列并核对终止条件', E'PYTHON\nCPP17'),
    ('IP_DQ4_MANHATTAN_SYMMETRY_CASE_SET',
     '提升点/几何坐标距离', '用对称样例验证曼哈顿距离',
     '作为已有坐标距离样例组的专项深化，聚焦有向位移与无向距离、绝对值和轴顺序。',
     'SK_V13_GEOMETRY_COORD_DISTANCE_CONTRACT', 'MATH.GEOMETRY.DIST.曼哈顿距离',
     E'MATH.GEOMETRY.COORD.行列坐标\nMATH.GEOMETRY.DIST.曼哈顿距离\nMATH.GEOMETRY.RELATION.边界包含',
     E'MP_DQ4_MANHATTAN_DELTA_MISSING_ABSOLUTE_VALUE\nMP_V13_GEOMETRY_ROW_COL_TREATED_AS_XY\nMP_V13_GEOMETRY_USES_EUCLIDEAN_WHEN_MANHATTAN_REQUIRED\nMP_V13_GEOMETRY_BOUNDARY_POINT_EXCLUDED_BY_STRICT_COMPARE',
     '让距离公式满足非负、对称、同点为零，并与允许移动方式一致。',
     '构造交换两点、负坐标、同轴点、矩阵行列和边界点五组样例；逐组检查 d(a,b)=d(b,a)、d≥0 和手算最少步数。',
     '能区分坐标差、曼哈顿距离和欧氏距离，公式错误可由性质直接发现。',
     '教师交换端点或翻转某一坐标符号，要求学生不重算完整路径就判断距离是否应保持。',
     '用对称性和非负性验证距离公式', E'PYTHON\nCPP17'),
    ('IP_DQ4_COUNTEREXAMPLE_DELTA_DEBUGGING',
     '提升点/调试方法', '用单变量缩减法得到最小反例',
     '作为已有最小反例轨迹的专项深化，聚焦每次只改一个因素并持续保留失败。',
     'SK_DEBUG_MINIMAL_COUNTEREXAMPLE', 'CONTEST.SUBMIT.REVIEW.最小反例构造',
     E'ENG.DEBUG.SAMPLE.最小反例\nENG.DEBUG.SAMPLE.手算对照\nCONTEST.SUBMIT.REVIEW.最小反例构造',
     E'MP_DQ4_COUNTEREXAMPLE_CHANGES_MULTIPLE_FACTORS\nMP_DEBUG_ONLY_RETESTS_SAMPLE',
     '把不可解释的大失败输入缩成可手算、可重复、只保留根因的最小反例。',
     '对一条失败数据按“删除一半、缩小数值、简化结构”依次操作，每次只做一项并记录 PASS/FAIL；回退任何让失败消失的修改，直到无法继续缩小。',
     '调试证据更清晰，修复可以针对第一处分叉而不是随机改代码。',
     '检查缩减日志必须能说明每一步唯一变化和失败是否保留；无日志的大幅重写不算最小化。',
     '记录单变量缩减日志并手算最小例', E'PYTHON\nCPP17'),
    ('IP_DQ4_MAX_SCALE_RESOURCE_LEDGER',
     '提升点/复杂度空间', '建立最大规模时间空间总账',
     '作为已有复杂度预算表的专项深化，要求把多个约束同时取最大并计算峰值并存资源。',
     'SK_V10_COMPLEXITY_SPACE_CONTRACT', 'ENG.DEBUG.BOUNDARY.最大输入',
     E'ENG.DEBUG.BOUNDARY.最大输入\nENG.COMPLEXITY.TIME.数据范围反推\nENG.COMPLEXITY.SPACE.二维空间\nENG.COMPLEXITY.TRADEOFF.预处理收益判断\nCONTEST.READING.CONSTRAINT.内存限制',
     E'MP_DQ4_COMPLEXITY_ESTIMATES_AVERAGE_NOT_MAXIMUM_SCALE\nMP_V10_SPACE_TABLE_SIZE_NOT_ESTIMATED\nMP_V10_CACHE_KEY_EXPLODES_STATE_SPACE\nMP_V10_ROLLING_ARRAY_OVERWRITES_REQUIRED_LAYER',
     '在提交前证明最大约束组合下时间、内存和递归深度都在预算内。',
     '填写“结构或循环、数量、单项成本、总成本、峰值是否并存”总账；把 n、m、q 和状态维度同时代入，再生成最大结构做限时压力测试。',
     '避免平均样例掩盖 TLE、MLE 和栈溢出，优化目标能落到真正瓶颈。',
     '教师随机把一个约束提高十倍，要求学生立即更新总账并指出最先失效的资源。',
     '填写资源总账并运行最大规模压力测试', E'PYTHON\nCPP17'),
    ('IP_DQ4_FAILURE_SIGNAL_LADDER',
     '提升点/调试复盘', '按第一条可靠信号建立错误分类阶梯',
     '作为已有错误类型工作表的专项深化，聚焦编译、退出状态、首个输出分叉和资源信号的因果顺序。',
     'SK_V10_DEBUG_ERROR_TRIAGE_CONTRACT', 'CONTEST.SUBMIT.REVIEW.错误类型定位',
     E'CONTEST.SUBMIT.REVIEW.错误类型定位\nENG.ERROR.COMPILE.语法拼写\nENG.ERROR.RUNTIME.空容器访问保护\nENG.ERROR.WRONG_ANSWER.样例通过隐藏失败\nENG.ERROR.TIME.复杂度过高',
     E'MP_DQ4_ERROR_CLASSIFIED_FROM_LAST_MESSAGE\nMP_V10_DEBUG_COUNTEREXAMPLE_NOT_MINIMIZED\nMP_V10_DEBUG_TRACE_WITHOUT_EXPECTED_VALUES\nMP_V10_DEBUG_WA_TREATED_AS_FORMAT_ONLY',
     '从最早可靠异常确定排查入口，再沿证据定位具体代码、状态或复杂度根因。',
     '对 CE、RE、WA、TLE 各一例填写“第一信号、下一证据、最小复现、第一处分叉、根因假设”；故意加入连锁日志，练习忽略后续噪声。',
     '减少围绕末尾报错和表面格式盲改，错误类型变化时仍能保留因果链。',
     '教师打乱日志顺序，要求学生按发生阶段重新排序并指出第一条可靠异常。',
     '按发生阶段重排信号并定位根因', E'PYTHON\nCPP17'),
    ('IP_DQ4_REGRESSION_NEIGHBORHOOD_MATRIX',
     '提升点/提交调试', '用相邻风险矩阵验证修复不是特判',
     '作为已有复现修复闭环的专项深化，要求每次修复同时覆盖原失败、相邻边界和同类变式。',
     'SK_V6_DEBUG_SUBMISSION_FEEDBACK_LOOP', 'ENG.ERROR.WRONG_ANSWER.边界遗漏',
     E'ENG.ERROR.WRONG_ANSWER.边界遗漏\nENG.ERROR.WRONG_ANSWER.样例通过隐藏失败\nCONTEST.SUBMIT.CHECKLIST.边界复测\nCONTEST.SUBMIT.REVIEW.最小反例构造',
     E'MP_DQ4_FIX_PATCHES_SYMPTOM_WITHOUT_ROOT_CAUSE\nMP_V6_DEBUG_FIX_WITHOUT_REPRODUCING_FAILURE\nMP_V6_DEBUG_ONLY_CHECKS_CHANGED_SAMPLE\nMP_V6_TLE_OPTIMIZES_CONSTANT_NOT_COMPLEXITY',
     '让修复对应可说明的根因，并证明它覆盖同一合同附近的输入而非只匹配一个样例。',
     '每次修复填写“根因、改动规则、原失败、边界-1、边界、边界+1、同类随机例、最大规模”矩阵；所有格先写期望再运行。',
     '减少修一例坏一类和条件特判堆积，一次失败能沉淀为稳定回归资产。',
     '检查代码差异必须能回指根因，且矩阵至少包含三类不同证据；只有原失败变绿不算完成。',
     '填写修复回归矩阵并覆盖相邻边界', E'PYTHON\nCPP17');

INSERT INTO public.ai_standard_improvement_points (
    applicable_languages, category, code, created_at, description, enabled,
    improvement_goal, knowledge_node_codes, library_version, name,
    practice_strategy, primary_knowledge_node_code, related_mistake_codes,
    skill_unit_code, student_benefit, teacher_explanation, updated_at
)
SELECT v.applicable_languages, v.category, v.code, CURRENT_TIMESTAMP, v.description, true,
       v.improvement_goal, v.knowledge_node_codes, 'informatics-discipline-quality-v4', v.name,
       v.practice_strategy, v.primary_knowledge_node_code, v.related_mistake_codes,
       v.skill_unit_code, v.student_benefit, v.teacher_explanation, CURRENT_TIMESTAMP
FROM discipline_quality_v4_improvements v
JOIN public.ai_standard_skill_units s
  ON s.code = v.skill_unit_code
 AND s.enabled = true
 AND s.code NOT LIKE 'SK_COMPAT_%'
 AND s.description NOT LIKE '%用于兼容旧 AI 标准库标签%'
JOIN public.informatics_knowledge_nodes n
  ON n.code = v.primary_knowledge_node_code AND n.enabled = true AND n.type = 'KNOWLEDGE_POINT'
WHERE NOT EXISTS (
    SELECT 1
    FROM regexp_split_to_table(v.related_mistake_codes, E'\n') ref
    LEFT JOIN public.ai_standard_mistake_points m
      ON m.code = btrim(ref) AND m.enabled = true AND m.skill_unit_code = v.skill_unit_code
    WHERE btrim(ref) <> '' AND m.code IS NULL
)
ON CONFLICT (code) DO UPDATE SET
    applicable_languages = EXCLUDED.applicable_languages,
    category = EXCLUDED.category,
    description = EXCLUDED.description,
    enabled = EXCLUDED.enabled,
    improvement_goal = EXCLUDED.improvement_goal,
    knowledge_node_codes = EXCLUDED.knowledge_node_codes,
    library_version = EXCLUDED.library_version,
    name = EXCLUDED.name,
    practice_strategy = EXCLUDED.practice_strategy,
    primary_knowledge_node_code = EXCLUDED.primary_knowledge_node_code,
    related_mistake_codes = EXCLUDED.related_mistake_codes,
    skill_unit_code = EXCLUDED.skill_unit_code,
    student_benefit = EXCLUDED.student_benefit,
    teacher_explanation = EXCLUDED.teacher_explanation,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO public.ai_standard_library_items (
    ability_point, applicable_languages, category, code, created_at, description,
    enabled, knowledge_node_codes, layer, library_version, name, related_items,
    severity, skill_unit_code, student_benefit, teacher_explanation, teaching_action,
    updated_at, when_to_use, primary_knowledge_node_code, related_knowledge_node_codes
)
SELECT v.name, v.applicable_languages, v.category, v.code, CURRENT_TIMESTAMP, v.description,
       true, v.knowledge_node_codes, 'IMPROVEMENT_POINT', 'informatics-discipline-quality-v4',
       v.name, v.related_mistake_codes, 'MEDIUM', v.skill_unit_code, v.student_benefit,
       v.teacher_explanation, v.teaching_action, CURRENT_TIMESTAMP, v.improvement_goal,
       v.primary_knowledge_node_code, v.knowledge_node_codes
FROM discipline_quality_v4_improvements v
JOIN public.ai_standard_improvement_points i
  ON i.code = v.code AND i.enabled = true
ON CONFLICT (layer, code) DO UPDATE SET
    ability_point = EXCLUDED.ability_point,
    applicable_languages = EXCLUDED.applicable_languages,
    category = EXCLUDED.category,
    description = EXCLUDED.description,
    enabled = EXCLUDED.enabled,
    knowledge_node_codes = EXCLUDED.knowledge_node_codes,
    library_version = EXCLUDED.library_version,
    name = EXCLUDED.name,
    related_items = EXCLUDED.related_items,
    severity = EXCLUDED.severity,
    skill_unit_code = EXCLUDED.skill_unit_code,
    student_benefit = EXCLUDED.student_benefit,
    teacher_explanation = EXCLUDED.teacher_explanation,
    teaching_action = EXCLUDED.teaching_action,
    updated_at = CURRENT_TIMESTAMP,
    when_to_use = EXCLUDED.when_to_use,
    primary_knowledge_node_code = EXCLUDED.primary_knowledge_node_code,
    related_knowledge_node_codes = EXCLUDED.related_knowledge_node_codes;

INSERT INTO public.ai_standard_library_legacy_mappings (
    confidence, created_at, legacy_code, legacy_layer, migration_status,
    source_version, target_code, target_type, updated_at
)
SELECT 1.0, CURRENT_TIMESTAMP, v.code, 'IMPROVEMENT_POINT', 'MAPPED',
       'informatics-discipline-quality-v4', v.code, 'IMPROVEMENT_POINT', CURRENT_TIMESTAMP
FROM discipline_quality_v4_improvements v
JOIN public.ai_standard_improvement_points i
  ON i.code = v.code AND i.enabled = true
ON CONFLICT (legacy_layer, legacy_code) DO UPDATE SET
    confidence = EXCLUDED.confidence,
    migration_status = EXCLUDED.migration_status,
    source_version = EXCLUDED.source_version,
    target_code = EXCLUDED.target_code,
    target_type = EXCLUDED.target_type,
    updated_at = CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.ai_standard_skill_units) THEN
        IF (SELECT count(*) FROM public.ai_standard_improvement_points
            WHERE enabled = true AND library_version = 'informatics-discipline-quality-v4') <> 22 THEN
            RAISE EXCEPTION 'V6 expected exactly 22 normalized improvement points';
        END IF;

        IF (SELECT count(DISTINCT i.skill_unit_code)
            FROM public.ai_standard_improvement_points i
            WHERE i.enabled = true
              AND i.library_version = 'informatics-discipline-quality-v4'
              AND i.skill_unit_code IN (
                  'SK_BRANCH_CASE_COVERAGE',
                  'SK_FUNCTION_CONTRACT',
                  'SK_INDEX_BASE_MAPPING',
                  'SK_INTEGER_RANGE_PROTECTION',
                  'SK_IO_STRUCTURE_MAPPING',
                  'SK_MATRIX_COORDINATE_MAPPING',
                  'SK_RECURSION_BACKTRACK_STATE',
                  'SK_RECURSION_BASE_PROGRESS',
                  'SK_STRING_SLICE_ENDPOINT',
                  'SK_V7_MULTICASE_STATE_RESET_CONTRACT',
                  'SK_V7_NUMERIC_RANGE_PRECISION_CONTRACT',
                  'SK_V7_STRING_BOUNDARY_ENCODING_CONTRACT',
                  'SK_READING_OBJECTIVE_CONSTRAINT',
                  'SK_READING_SAMPLE_CONSTRAINT_CROSSCHECK'
              )) <> 14 THEN
            RAISE EXCEPTION 'V6 expected 14 first-path skills to receive improvements';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.ai_standard_improvement_points i
            JOIN public.ai_standard_skill_units s ON s.code = i.skill_unit_code
            WHERE i.enabled = true
              AND i.library_version = 'informatics-discipline-quality-v4'
              AND (s.code LIKE 'SK_COMPAT_%'
                OR s.description LIKE '%用于兼容旧 AI 标准库标签%')
        ) THEN
            RAISE EXCEPTION 'V6 formal improvement points must not target compatibility skills';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.ai_standard_improvement_points i
            CROSS JOIN LATERAL regexp_split_to_table(COALESCE(i.related_mistake_codes, ''), E'\n') ref
            LEFT JOIN public.ai_standard_mistake_points m
              ON m.code = btrim(ref) AND m.enabled = true AND m.skill_unit_code = i.skill_unit_code
            WHERE i.enabled = true
              AND i.library_version = 'informatics-discipline-quality-v4'
              AND btrim(ref) <> '' AND m.code IS NULL
        ) THEN
            RAISE EXCEPTION 'V6 improvement contains invalid or cross-skill mistake reference';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.ai_standard_mistake_points m
            WHERE m.enabled = true
              AND m.library_version = 'informatics-discipline-quality-v4'
              AND NOT EXISTS (
                  SELECT 1
                  FROM public.ai_standard_improvement_points i
                  CROSS JOIN LATERAL regexp_split_to_table(COALESCE(i.related_mistake_codes, ''), E'\n') ref
                  WHERE i.enabled = true
                    AND i.library_version = 'informatics-discipline-quality-v4'
                    AND i.skill_unit_code = m.skill_unit_code
                    AND btrim(ref) = m.code
              )
        ) THEN
            RAISE EXCEPTION 'V6 mistake is not linked by a same-skill V6 improvement';
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.ai_standard_improvement_points i
            LEFT JOIN public.ai_standard_library_items item
              ON item.layer = 'IMPROVEMENT_POINT' AND item.code = i.code
            LEFT JOIN public.ai_standard_library_legacy_mappings map
              ON map.legacy_layer = 'IMPROVEMENT_POINT' AND map.legacy_code = i.code
            WHERE i.enabled = true
              AND i.library_version = 'informatics-discipline-quality-v4'
              AND (item.id IS NULL OR item.enabled IS DISTINCT FROM true
                OR item.skill_unit_code IS DISTINCT FROM i.skill_unit_code
                OR item.primary_knowledge_node_code IS DISTINCT FROM i.primary_knowledge_node_code
                OR item.related_items IS DISTINCT FROM i.related_mistake_codes
                OR map.id IS NULL OR map.migration_status <> 'MAPPED'
                OR map.target_type <> 'IMPROVEMENT_POINT' OR map.target_code <> i.code)
        ) THEN
            RAISE EXCEPTION 'V6 improvement normalized, snapshot or mapping structures are inconsistent';
        END IF;

        IF (SELECT count(*)
            FROM public.ai_standard_skill_units s
            WHERE s.enabled = true
              AND NOT EXISTS (
                  SELECT 1 FROM public.ai_standard_improvement_points i
                  WHERE i.enabled = true AND i.skill_unit_code = s.code
              )) > 27 THEN
            RAISE EXCEPTION 'V6 expected skills without improvement to fall to at most 27';
        END IF;
    END IF;
END $$;
