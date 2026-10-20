package com.onlinejudge.learning.knowledge.application;

import com.onlinejudge.learning.knowledge.domain.InformaticsKnowledgeNodeType;

import java.util.ArrayList;
import java.util.List;

public final class InformaticsKnowledgeSeedCatalog {

    public static final String VERSION = "informatics-knowledge-v1";

    private InformaticsKnowledgeSeedCatalog() {
    }

    public static List<InformaticsKnowledgeSeed> seeds() {
        CatalogBuilder catalog = new CatalogBuilder();
        addProgrammingBasics(catalog);
        addDataStructures(catalog);
        addAlgorithmThinking(catalog);
        addMathAndModeling(catalog);
        addDebuggingAndEngineering(catalog);
        addContestStrategy(catalog);
        return catalog.seeds();
    }

    private static void addProgrammingBasics(CatalogBuilder catalog) {
        catalog.domain("BASIC", "程序设计基础", "掌握 C++/Python 程序设计的基础结构、数据表示、控制流程与模块化表达。", "初中-高中", 100);

        catalog.chapter("BASIC.IO", "BASIC", "输入输出", "理解标准输入输出、格式控制、多组数据和交互式读写的基础规则。", 110)
                .topics("BASIC.IO.STDIN", "标准输入读取", "单值读取", "多值同行读取", "多行读取", "读到文件结束", "混合数字与字符串读取", "输入顺序映射")
                .topics("BASIC.IO.STDOUT", "标准输出与格式", "按要求换行", "空格分隔", "小数精度控制", "禁止多余调试输出", "大小写与标点一致")
                .topics("BASIC.IO.MULTI_CASE", "多组数据", "显式 T 组循环", "未知组数读到 EOF", "每组状态重置", "多组输出格式", "样例单组与隐藏多组差异");

        catalog.chapter("BASIC.TYPE", "BASIC", "变量与数据类型", "理解变量、常量、类型范围、类型转换和溢出风险。", 120)
                .topics("BASIC.TYPE.VARIABLE", "变量与赋值", "变量命名", "声明与初始化", "赋值覆盖", "作用域", "常量表达")
                .topics("BASIC.TYPE.INTEGER", "整数类型", "int 范围", "long long 范围", "无符号风险", "整型溢出", "计数变量类型选择")
                .topics("BASIC.TYPE.FLOAT", "浮点类型", "浮点误差", "小数比较", "精度输出", "整数除法误用", "取整策略")
                .topics("BASIC.TYPE.CHAR_BOOL", "字符与布尔", "字符编码", "字符数字转换", "布尔表达式", "真假值隐式转换", "逻辑标志变量");

        catalog.chapter("BASIC.EXPR", "BASIC", "运算表达式", "掌握算术、关系、逻辑、取模和表达式优先级。", 130)
                .topics("BASIC.EXPR.ARITH", "算术运算", "加减乘除顺序", "整数除法", "取模含义", "负数取模", "复合赋值")
                .topics("BASIC.EXPR.COMPARE", "关系比较", "等于与赋值区分", "边界比较", "浮点比较", "字符比较", "字典序比较")
                .topics("BASIC.EXPR.LOGIC", "逻辑表达式", "与或非", "短路求值", "条件组合", "德摩根变换", "布尔变量简化")
                .topics("BASIC.EXPR.PRIORITY", "优先级与括号", "算术优先级", "逻辑优先级", "位运算优先级", "括号明确意图", "表达式拆分");

        catalog.chapter("BASIC.BRANCH", "BASIC", "条件分支", "理解 if/else、分类讨论、边界覆盖和分支互斥。", 140)
                .topics("BASIC.BRANCH.IF", "if 条件", "单分支判断", "双分支判断", "多分支链", "嵌套分支", "条件变量准备")
                .topics("BASIC.BRANCH.CASE", "分类讨论", "互斥条件", "覆盖所有情况", "边界归属", "优先级条件前置", "默认分支")
                .topics("BASIC.BRANCH.GUARD", "提前返回与保护条件", "非法输入保护", "特殊情况前置", "避免深层嵌套", "哨兵分支", "空结果处理");

        catalog.chapter("BASIC.LOOP", "BASIC", "循环结构", "掌握循环次数、循环边界、循环状态和嵌套循环。", 150)
                .topics("BASIC.LOOP.FOR", "for 循环", "起点设计", "终点设计", "步长变化", "循环次数计算", "循环变量不被破坏")
                .topics("BASIC.LOOP.WHILE", "while 循环", "循环条件", "状态推进", "死循环识别", "读入驱动循环", "哨兵终止")
                .topics("BASIC.LOOP.BOUNDARY", "循环边界", "左闭右开", "左闭右闭", "0 基下标循环", "1 基下标循环", "最后一次迭代")
                .topics("BASIC.LOOP.NESTED", "嵌套循环", "内外层变量区分", "枚举组合", "矩阵遍历", "重复初始化位置", "复杂度估算")
                .topics("BASIC.LOOP.CONTROL", "循环控制", "break 使用", "continue 使用", "提前结束", "标志变量", "多层循环退出");

        catalog.chapter("BASIC.ARRAY", "BASIC", "数组与序列", "理解线性存储、下标、遍历、更新和区间处理。", 160)
                .topics("BASIC.ARRAY.INDEX", "数组下标", "0 基下标", "1 基下标", "越界访问", "长度与最后下标", "下标映射")
                .topics("BASIC.ARRAY.TRAVERSE", "数组遍历", "全量遍历", "局部遍历", "逆序遍历", "步长遍历", "同步遍历", "擂台法求最值")
                .topics("BASIC.ARRAY.UPDATE", "数组更新", "原地修改", "临时数组", "覆盖风险", "交换元素", "累计更新", "读旧写新分离", "批量更新后还原顺序", "累计量同步更新")
                .topics("BASIC.ARRAY.PREFIX", "前缀统计基础", "前缀和定义", "区间和查询", "差分直觉", "计数数组", "频次统计")
                .topics("BASIC.ARRAY.MATRIX", "二维数组", "行列含义", "矩阵输入", "方向遍历", "边界检查", "坐标转换");

        catalog.chapter("BASIC.STRING", "BASIC", "字符串", "掌握字符序列、子串、查找、计数和常见文本处理。", 170)
                .topics("BASIC.STRING.CHAR", "字符处理", "字符遍历", "大小写转换", "数字字符转换", "字母判断", "特殊字符处理")
                .topics("BASIC.STRING.SUBSTRING", "子串与切片", "起止位置", "长度参数", "空串处理", "越界保护", "Python/C++ 切片差异")
                .topics("BASIC.STRING.MATCH", "字符串匹配基础", "逐位比较", "查找出现位置", "统计出现次数", "前后缀判断", "回文判断", "未找到结果哨兵", "重叠匹配计数", "前后缀边界")
                .topics("BASIC.STRING.BUILD", "字符串构造", "追加字符", "拼接效率", "格式化构造", "删除替换", "结果顺序", "游程编码");

        catalog.chapter("BASIC.FUNCTION", "BASIC", "函数与模块化", "理解函数参数、返回值、局部变量和代码复用。", 180)
                .topics("BASIC.FUNCTION.DEF", "函数定义", "参数设计", "返回值设计", "局部变量", "函数命名", "单一职责")
                .topics("BASIC.FUNCTION.PARAM", "参数传递", "值传递", "引用传递", "数组参数", "可变对象风险", "默认参数")
                .topics("BASIC.FUNCTION.RETURN", "返回与副作用", "完整返回路径", "提前返回", "返回布尔", "返回集合", "输出与返回分离");

        catalog.chapter("BASIC.RECURSION", "BASIC", "递归基础", "理解递归定义、边界、状态规模缩小和调用栈。", 190)
                .topics("BASIC.RECURSION.BASE", "递归边界", "终止条件", "最小规模", "多边界条件", "边界返回值", "防止无限递归")
                .topics("BASIC.RECURSION.STATE", "递归状态", "参数表示状态", "规模缩小", "递归转移", "回溯恢复", "调用栈理解")
                .topics("BASIC.RECURSION.DIVIDE", "分治雏形", "拆分问题", "合并结果", "二分递归", "树形递归", "复杂度递推");
    }

    private static void addDataStructures(CatalogBuilder catalog) {
        catalog.domain("DS", "数据结构基础", "理解常见数据组织方式及其操作复杂度，能为题目选择合适结构。", "高中", 200);

        catalog.chapter("DS.LINEAR", "DS", "线性结构", "掌握数组、动态数组、链式思维、栈和队列。", 210)
                .topics("DS.LINEAR.VECTOR", "动态数组", "追加元素", "随机访问", "删除移动", "容量变化", "遍历迭代")
                .topics("DS.LINEAR.STACK", "栈", "后进先出", "括号匹配", "单调栈雏形", "递归栈模拟", "空栈检查")
                .topics("DS.LINEAR.QUEUE", "队列", "先进先出", "BFS 队列", "循环队列", "双端队列", "队空判断")
                .topics("DS.LINEAR.LIST", "链表", "前驱后继", "插入删除", "指针/下标模拟", "头尾节点", "边界节点");

        catalog.chapter("DS.SET_MAP", "DS", "集合与映射", "掌握去重、计数、映射和有序结构的应用。", 220)
                .topics("DS.SET_MAP.SET", "集合 set", "去重", "成员判断", "有序遍历", "集合大小", "重复插入")
                .topics("DS.SET_MAP.MAP", "映射 map/dict", "键值关系", "频次统计", "默认值", "键不存在处理", "遍历键值")
                .topics("DS.SET_MAP.HASH", "哈希思想", "均摊复杂度", "哈希冲突直觉", "字符串键", "自定义键风险", "空间换时间");

        catalog.chapter("DS.TREE", "DS", "树结构", "理解父子关系、遍历、深度和常见树形建模。", 230)
                .topics("DS.TREE.BASIC", "树的基本概念", "根节点", "父子节点", "叶子节点", "深度高度", "子树")
                .topics("DS.TREE.TRAVERSAL", "树遍历", "DFS 遍历", "BFS 遍历", "前中后序", "递归遍历", "非递归遍历")
                .topics("DS.TREE.BINARY", "二叉树", "左右孩子", "二叉搜索树直觉", "堆结构直觉", "完全二叉树", "数组表示");

        catalog.chapter("DS.GRAPH", "DS", "图结构", "理解点、边、邻接关系、连通性和图遍历。", 240)
                .topics("DS.GRAPH.MODEL", "图建模", "点的定义", "边的定义", "有向无向", "权值含义", "状态转图")
                .topics("DS.GRAPH.STORE", "图存储", "邻接矩阵", "邻接表", "边列表", "稀疏稠密选择", "双向边添加", "无向边双向建边", "权值字段绑定", "多组图清空")
                .topics("DS.GRAPH.TRAVERSE", "图遍历", "DFS 连通块", "BFS 层次", "访问标记", "重复入队", "路径记录");
    }

    private static void addAlgorithmThinking(CatalogBuilder catalog) {
        catalog.domain("ALGO", "算法思想", "掌握信息学竞赛常见解题范式、复杂度意识和算法选择。", "高中-CSP", 300);

        catalog.chapter("ALGO.SIM", "ALGO", "模拟", "根据题意维护状态并按规则推进。", 310)
                .topics("ALGO.SIM.STATE", "状态设计", "状态变量选择", "状态初始化", "状态转移", "状态同步", "状态输出", "状态标记法", "多数投票算法")
                .topics("ALGO.SIM.PROCESS", "流程模拟", "事件顺序", "规则优先级", "多对象模拟", "时间推进", "终止条件", "状态更新先后", "事件队列推进")
                .topics("ALGO.SIM.CORNER", "模拟边界", "空状态", "首尾元素", "同一时刻冲突", "极限步数", "样例复现", "空队列空集合状态", "首尾事件同时发生", "并列规则冲突");

        catalog.chapter("ALGO.ENUM", "ALGO", "枚举与暴力优化", "枚举候选方案，并用剪枝、预处理或结构降低复杂度。", 320)
                .topics("ALGO.ENUM.LOOP", "枚举设计", "枚举对象", "枚举范围", "去重枚举", "组合枚举", "排列枚举")
                .topics("ALGO.ENUM.PRUNE", "剪枝", "不可能条件", "当前最优界", "重复状态跳过", "排序后剪枝", "提前终止")
                .topics("ALGO.ENUM.COMPLEXITY", "枚举复杂度", "一重循环", "二重循环", "三重循环", "指数枚举", "数据范围反推");

        catalog.chapter("ALGO.SORT", "ALGO", "排序与比较", "掌握排序调用、比较规则、稳定性和排序后的结构利用。", 330)
                .topics("ALGO.SORT.BASIC", "排序基础", "升序降序", "自定义比较", "稳定排序", "原索引保存", "排序后遍历", "选择排序", "冒泡排序", "计数排序")
                .topics("ALGO.SORT.APPLICATION", "排序应用", "贪心排序", "区间排序", "去重统计", "中位数", "相邻差值", "分组排序");

        catalog.chapter("ALGO.BINARY", "ALGO", "二分", "在有序性或单调性上查找答案或位置。", 340)
                .topics("ALGO.BINARY.INDEX", "位置二分", "闭区间模板", "半开区间模板", "lower_bound", "upper_bound", "边界返回")
                .topics("ALGO.BINARY.ANSWER", "答案二分", "单调性证明", "check 函数", "左右边界", "最大最小答案", "精度二分");

        catalog.chapter("ALGO.PREFIX", "ALGO", "前缀和与差分", "用预处理支持区间查询或批量区间更新。", 350)
                .topics("ALGO.PREFIX.SUM", "一维前缀和", "前缀定义", "区间查询", "下标偏移", "空前缀", "long long 累计")
                .topics("ALGO.PREFIX.DIFF", "一维差分", "区间加", "差分还原", "边界加减", "多次更新", "离线处理")
                .topics("ALGO.PREFIX.MATRIX", "二维前缀和", "容斥公式", "坐标偏移", "子矩阵查询", "边界补零", "行列混淆");

        catalog.chapter("ALGO.TWO_POINTERS", "ALGO", "双指针与滑动窗口", "利用单调移动维护区间或配对关系。", 360)
                .topics("ALGO.TWO_POINTERS.OPPOSITE", "对向双指针", "有序数组配对", "左右收缩", "去重移动", "最优性判断", "终止条件", "左右指针移动依据", "相等元素去重策略")
                .topics("ALGO.TWO_POINTERS.WINDOW", "滑动窗口", "窗口扩张", "窗口收缩", "计数维护", "合法性判断", "答案更新时机");

        catalog.chapter("ALGO.SEARCH", "ALGO", "搜索", "用 DFS/BFS 探索状态空间，并控制访问、剪枝和恢复。", 370)
                .topics("ALGO.SEARCH.DFS", "深度优先搜索", "递归参数", "搜索边界", "路径记录", "回溯恢复", "剪枝位置")
                .topics("ALGO.SEARCH.BFS", "广度优先搜索", "队列层次", "最短步数", "访问标记", "多源 BFS", "状态入队")
                .topics("ALGO.SEARCH.STATE", "状态空间", "状态编码", "判重", "状态转移生成", "非法状态过滤", "终点判断");

        catalog.chapter("ALGO.GREEDY", "ALGO", "贪心", "基于局部选择构造全局最优，并能说明交换或排序依据。", 380)
                .topics("ALGO.GREEDY.CHOICE", "贪心选择", "局部最优", "排序依据", "选择时机", "反例检查", "交换论证")
                .topics("ALGO.GREEDY.INTERVAL", "区间调度与合并", "按右端点排序", "区间覆盖", "区间合并", "不相交选择", "边界重叠")
                .topics("ALGO.GREEDY.STRUCTURE", "结构化贪心", "优先队列辅助", "最小代价合并", "延迟选择", "资源分配", "可行性维护");

        catalog.chapter("ALGO.DP", "ALGO", "动态规划", "用状态、转移、初值和计算顺序解决重叠子问题。", 390)
                .topics("ALGO.DP.STATE", "DP 状态设计", "状态含义", "维度选择", "状态压缩直觉", "答案位置", "非法状态")
                .topics("ALGO.DP.TRANSITION", "DP 转移", "从前往后", "从后往前", "枚举决策", "转移边界", "取 max/min/sum")
                .topics("ALGO.DP.INIT", "DP 初始化", "初始值", "无穷大", "边界状态", "空方案", "滚动数组初始化")
                .topics("ALGO.DP.CLASSIC", "经典 DP", "背包 DP", "线性 DP", "区间 DP 入门", "树形 DP 入门", "计数 DP");

        catalog.chapter("ALGO.GRAPH", "ALGO", "图算法", "掌握连通性、最短路、拓扑序和最小生成树的基础思想。", 400)
                .topics("ALGO.GRAPH.CONNECT", "连通性", "连通块", "并查集", "路径压缩", "合并方向", "重复边")
                .topics("ALGO.GRAPH.SHORTEST", "最短路", "BFS 最短路", "Dijkstra", "负权限制", "距离初始化", "松弛操作")
                .topics("ALGO.GRAPH.TOPO", "拓扑排序", "入度", "队列推进", "环检测", "依赖关系", "多解顺序")
                .topics("ALGO.GRAPH.MST", "最小生成树", "Kruskal", "Prim 直觉", "边排序", "并查集合并", "连通性检查");
    }

    private static void addMathAndModeling(CatalogBuilder catalog) {
        catalog.domain("MATH", "数学与建模", "掌握信息学题目常用数学工具、建模表达和离散思维。", "高中-CSP", 500);

        catalog.chapter("MATH.NUMBER", "MATH", "数论基础", "整除、质数、最大公约数、取模和同余。", 510)
                .topics("MATH.NUMBER.DIVISIBILITY", "整除与因子", "倍数判断", "因子枚举", "平方根优化", "约数个数", "完全平方")
                .topics("MATH.NUMBER.PRIME", "质数", "试除判定", "筛法", "质因数分解", "1 不是质数", "大数范围")
                .topics("MATH.NUMBER.GCD", "最大公约数", "欧几里得算法", "最小公倍数", "除零边界", "多数字 gcd", "约分")
                .topics("MATH.NUMBER.MOD", "取模运算", "加法取模", "乘法取模", "负数取模修正", "大数防溢出", "模意义保持");

        catalog.chapter("MATH.COUNT", "MATH", "计数与组合", "理解排列组合、容斥、递推和计数状态。", 520)
                .topics("MATH.COUNT.PERM", "排列组合基础", "排列", "组合", "重复选择", "顺序是否重要", "除重")
                .topics("MATH.COUNT.INCLUSION", "容斥思想", "集合重叠", "二集合容斥", "多条件计数", "反面计数", "边界全集")
                .topics("MATH.COUNT.RECUR", "递推计数", "递推式", "初始项", "滚动计算", "大数取模", "斐波那契模型");

        catalog.chapter("MATH.BIT", "MATH", "位运算", "理解二进制表示、位操作和状态压缩基础。", 530)
                .topics("MATH.BIT.BINARY", "二进制表示", "位权", "奇偶判断", "二进制长度", "补码直觉", "移位")
                .topics("MATH.BIT.OP", "位操作", "与或异或", "取某一位", "设置清除位", "lowbit", "异或性质")
                .topics("MATH.BIT.MASK", "状态压缩", "集合掩码", "枚举子集", "状态判断", "位数限制", "掩码转移");

        catalog.chapter("MATH.GEOMETRY", "MATH", "坐标与几何", "掌握坐标建模、距离、方向和简单几何判断。", 540)
                .topics("MATH.GEOMETRY.COORD", "坐标系统", "行列坐标", "x/y 坐标", "方向数组", "坐标偏移", "越界判断")
                .topics("MATH.GEOMETRY.DIST", "距离", "曼哈顿距离", "欧氏距离平方", "切比雪夫距离", "最短步数", "距离比较")
                .topics("MATH.GEOMETRY.RELATION", "几何关系", "共线", "方向判断", "矩形覆盖", "区间交", "边界包含");
    }

    private static void addDebuggingAndEngineering(CatalogBuilder catalog) {
        catalog.domain("ENG", "调试与工程习惯", "掌握编译、运行、调试、复杂度估计和可读性习惯。", "初中-高中", 600);

        catalog.chapter("ENG.ERROR", "ENG", "错误类型", "区分编译错误、运行错误、答案错误、超时和格式错误。", 610)
                .topics("ENG.ERROR.COMPILE", "编译错误", "语法拼写", "缺少分号", "括号不匹配", "类型不匹配", "未声明标识符")
                .topics("ENG.ERROR.RUNTIME", "运行错误", "数组越界", "除零", "空容器访问", "递归爆栈", "非法内存", "空容器访问保护", "递归深度上限")
                .topics("ENG.ERROR.WRONG_ANSWER", "答案错误", "样例通过隐藏失败", "边界遗漏", "状态未重置", "逻辑分支遗漏", "精度错误")
                .topics("ENG.ERROR.TIME", "超时", "复杂度过高", "死循环", "重复计算", "低效 IO", "搜索爆炸")
                .topics("ENG.ERROR.FORMAT", "格式错误", "多余空格", "缺少换行", "调试输出", "大小写错误", "小数位错误");

        catalog.chapter("ENG.DEBUG", "ENG", "调试方法", "用最小样例、断点思维和状态观察定位问题。", 620)
                .topics("ENG.DEBUG.SAMPLE", "样例复现", "逐行模拟", "中间变量打印", "最小反例", "手算对照", "隐藏差异猜测")
                .topics("ENG.DEBUG.BOUNDARY", "边界测试", "最小输入", "最大输入", "空结果", "重复元素", "极端值")
                .topics("ENG.DEBUG.TRACE", "状态追踪", "循环状态", "数组状态", "递归栈", "队列变化", "DP 表变化");

        catalog.chapter("ENG.COMPLEXITY", "ENG", "复杂度意识", "根据数据范围选择时间和空间复杂度。", 630)
                .topics("ENG.COMPLEXITY.TIME", "时间复杂度", "O(n)", "O(n log n)", "O(n^2)", "指数复杂度", "数据范围反推")
                .topics("ENG.COMPLEXITY.SPACE", "空间复杂度", "数组规模", "二维空间", "递归栈", "哈希空间", "滚动优化")
                .topics("ENG.COMPLEXITY.TRADEOFF", "时空权衡", "预处理", "缓存", "空间换时间", "离线处理", "重复计算消除", "预处理收益判断", "空间换时间边界");

        catalog.chapter("ENG.STYLE", "ENG", "代码可读性", "用清晰命名、结构拆分和不变量表达降低错误率。", 640)
                .topics("ENG.STYLE.NAME", "命名", "变量含义", "下标命名", "布尔命名", "函数命名", "避免混淆")
                .topics("ENG.STYLE.STRUCTURE", "结构", "输入处理分离", "计算逻辑分离", "输出分离", "函数拆分", "重复代码消除")
                .topics("ENG.STYLE.INVARIANT", "不变量", "循环不变量", "窗口不变量", "DP 状态含义", "贪心维护量", "边界注释");
    }

    private static void addContestStrategy(CatalogBuilder catalog) {
        catalog.domain("CONTEST", "竞赛策略与题型识别", "训练读题、建模、题型识别和提交前检查。", "高中-CSP", 700);

        catalog.chapter("CONTEST.READING", "CONTEST", "读题与建模", "把自然语言题意转化为输入、状态、约束和目标。", 710)
                .topics("CONTEST.READING.INPUT", "输入结构识别", "数据组数", "数组规模", "矩阵规模", "图结构", "特殊符号")
                .topics("CONTEST.READING.OUTPUT", "输出目标识别", "输出最值", "输出方案数", "输出路径", "输出是否可行", "多答案要求")
                .topics("CONTEST.READING.CONSTRAINT", "约束条件提取", "数据范围", "值域范围", "时间限制", "内存限制", "隐藏特殊条件");

        catalog.chapter("CONTEST.PATTERN", "CONTEST", "题型识别", "根据输入规模、目标和结构选择算法范式。", 720)
                .topics("CONTEST.PATTERN.RANGE", "数据范围反推", "小范围暴力", "中等范围优化", "大范围 O(n log n)", "指数搜索边界", "空间限制")
                .topics("CONTEST.PATTERN.STRUCTURE", "结构特征", "有序性", "单调性", "区间性", "图结构", "重叠子问题")
                .topics("CONTEST.PATTERN.OBJECTIVE", "目标特征", "最值", "计数", "可行性", "构造", "路径");

        catalog.chapter("CONTEST.SUBMIT", "CONTEST", "提交前检查", "提交前系统检查代码、边界和复杂度。", 730)
                .topics("CONTEST.SUBMIT.CHECKLIST", "提交检查清单", "样例复测", "边界复测", "初始化检查", "输出格式检查", "复杂度检查", "多组状态复查", "溢出风险复查")
                .topics("CONTEST.SUBMIT.REVIEW", "失败后复盘", "错误类型定位", "最小反例构造", "同类错因记录", "知识点回补", "再次提交策略");
    }

    private static final class CatalogBuilder {
        private final List<InformaticsKnowledgeSeed> seeds = new ArrayList<>();

        List<InformaticsKnowledgeSeed> seeds() {
            return List.copyOf(seeds);
        }

        void domain(String code, String name, String description, String stage, int sortOrder) {
            add(code, "", InformaticsKnowledgeNodeType.DOMAIN, name, description, name, stage, "CORE",
                    List.of(), List.of(), List.of("建立" + name + "的整体知识框架。"), List.of(), sortOrder);
        }

        ChapterBuilder chapter(String code, String parentCode, String name, String description, int sortOrder) {
            String path = path(parentCode) + " / " + name;
            add(code, parentCode, InformaticsKnowledgeNodeType.CHAPTER, name, description, path, "中学信息学", "CORE",
                    List.of(), List.of(parentCode), List.of("理解" + name + "的核心概念。"), List.of(), sortOrder);
            return new ChapterBuilder(this, code, path, sortOrder);
        }

        private void add(String code,
                         String parentCode,
                         InformaticsKnowledgeNodeType type,
                         String name,
                         String description,
                         String path,
                         String stage,
                         String difficulty,
                         List<String> aliases,
                         List<String> prerequisites,
                         List<String> learningObjectives,
                         List<String> typicalProblems,
                         int sortOrder) {
            seeds.add(new InformaticsKnowledgeSeed(
                    code,
                    parentCode,
                    type,
                    name,
                    description,
                    path,
                    stage,
                    difficulty,
                    aliases,
                    prerequisites,
                    learningObjectives,
                    typicalProblems,
                    sortOrder,
                    VERSION
            ));
        }

        private String path(String code) {
            return seeds.stream()
                    .filter(seed -> seed.code().equals(code))
                    .map(InformaticsKnowledgeSeed::path)
                    .findFirst()
                    .orElse(code);
        }
    }

    private static final class ChapterBuilder {
        private final CatalogBuilder catalog;
        private final String chapterCode;
        private final String chapterPath;
        private final int chapterSort;
        private int topicIndex = 0;

        private ChapterBuilder(CatalogBuilder catalog, String chapterCode, String chapterPath, int chapterSort) {
            this.catalog = catalog;
            this.chapterCode = chapterCode;
            this.chapterPath = chapterPath;
            this.chapterSort = chapterSort;
        }

        ChapterBuilder topics(String topicCode, String topicName, String... knowledgePoints) {
            int topicSort = chapterSort + (++topicIndex);
            String topicPath = chapterPath + " / " + topicName;
            catalog.add(topicCode, chapterCode, InformaticsKnowledgeNodeType.TOPIC, topicName,
                    topicName + "相关的概念、方法和常见题型。",
                    topicPath, "中学信息学", "CORE", aliasesFor(topicCode, topicName), List.of(chapterCode),
                    List.of("掌握" + topicName + "的基本用法。"), List.of(), topicSort);
            for (int i = 0; i < knowledgePoints.length; i++) {
                String pointName = knowledgePoints[i];
                String pointCode = topicCode + "." + normalize(pointName);
                catalog.add(pointCode, topicCode, InformaticsKnowledgeNodeType.KNOWLEDGE_POINT, pointName,
                        "细颗粒知识点：" + pointName + "。需要能在代码、判题结果和调试过程里识别对应表现。",
                        topicPath + " / " + pointName, "中学信息学", i < 2 ? "BASIC" : "ADVANCED",
                        aliasesFor(pointCode, pointName), List.of(topicCode),
                        List.of("能解释" + pointName + "的含义。", "能识别" + pointName + "相关常见错误。"),
                        List.of(pointName + "相关小题", pointName + "相关调试样例"),
                        topicSort * 100 + i);
            }
            return this;
        }

        private List<String> aliasesFor(String code, String name) {
            List<String> aliases = new ArrayList<>();
            aliases.add(name);
            for (String alias : customAliases(code)) {
                if (!alias.isBlank() && !aliases.contains(alias)) {
                    aliases.add(alias);
                }
            }
            return List.copyOf(aliases);
        }

        private List<String> customAliases(String code) {
            return switch (code) {
                case "DS.LINEAR.LIST" -> List.of("链式思想", "链式结构", "链式存储", "linked list");
                case "ALGO.GREEDY.INTERVAL" -> List.of("区间调度", "区间贪心", "不相交区间", "interval scheduling");
                case "ALGO.SORT.BASIC.选择排序" -> List.of("selection sort", "选择法排序");
                case "ALGO.SORT.BASIC.冒泡排序" -> List.of("bubble sort", "交换排序");
                case "ALGO.SORT.BASIC.计数排序" -> List.of("counting sort", "计数数组排序");
                case "ALGO.SORT.APPLICATION.分组排序" -> List.of("分桶排序思想", "按组排序", "bucket grouping");
                case "BASIC.ARRAY.TRAVERSE.擂台法求最值" -> List.of("最值维护", "最大值扫描", "最小值扫描", "打擂台");
                case "BASIC.STRING.BUILD.游程编码" -> List.of("运行长度编码", "RLE", "数据压缩");
                case "ALGO.SIM.STATE.状态标记法" -> List.of("flag", "标志变量", "状态变量", "状态维护");
                case "ALGO.SIM.STATE.多数投票算法" -> List.of("Boyer-Moore", "候选值投票", "过半元素");
                case "DS.LINEAR.LIST.指针_下标模拟" -> List.of("数组模拟链表", "静态链表", "链表指针模拟");
                case "ALGO.GREEDY.INTERVAL.不相交选择" -> List.of("区间调度", "活动选择", "不相交区间集");
                case "MATH.COUNT.RECUR.斐波那契模型" -> List.of("斐波那契数列", "Fibonacci");
                case "MATH.NUMBER.PRIME.试除判定" -> List.of("素数判断", "质数判断");
                default -> List.of();
            };
        }

        private String normalize(String value) {
            return value
                    .replace("C++", "CPP")
                    .replace("O(n log n)", "NLOGN")
                    .replace("O(n^2)", "NSQUARE")
                    .replace("O(n)", "LINEAR")
                    .replace("PYTHON", "PY")
                    .replaceAll("[^A-Za-z0-9\\p{IsHan}]+", "_")
                    .replaceAll("^_+|_+$", "");
        }
    }
}
