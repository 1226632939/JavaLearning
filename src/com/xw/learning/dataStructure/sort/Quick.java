package com.xw.learning.dataStructure.sort;
/**
 * @program: Learning
 * @description: 快速排序
 * @author:
 *          切分原理：
 *          1.找一个基准值，用两个执政分别指向数组的头部和尾部；
 *          2.先从尾部想头部开始搜索一个比基准值小的元素，搜索到即停止，并记录之后指针的位置；
 *          3.再从头部向尾部开始搜索一个比基准值大的元素，搜索到即停止，并记录指针的位置；
 *          4.交换当前左边指针和右边指针的元素
 *          5.重复2.3.4步骤，知道左边指针的值大于右边指针的值停止。
 *
 *          时间复杂度分析：
 *          1.最优情况下 O(nlogn) 每次能找到最优分界值
 *          2.最欢情况下 O(n^2)
 *          3.平均情况下 o(nlogn)
 * @create: 2021-04-29 15:23
 **/
public class Quick {
    // 对数组内的元素进行排序
    public static void sort(Comparable[] a){
        int lo = 0;
        int hi = a.length-1;
        sort(a,lo,hi);
    }

    // 对数组a中从碎银lo到索引hi之间的元素进行排序
    private static void sort(Comparable[] a,int lo, int hi){
        // 安全性校验
        if (hi <= lo)
            return;
        // 需要对数组中lo索引到hi索引处的元素进行分组（左子组和右子组）
       int partition = partition(a,lo,hi);
        // 让左子组有序
        sort(a,lo,partition - 1);
        // 让右子组有序
        sort(a,partition + 1,hi);
    }

    // 对数组a中，从索引lo到索引hi之间的元素惊醒分组，并返回分组界限对应的索引
    public static int partition(Comparable[] a, int lo ,int hi){
        // 确定分界值
        Comparable key = a [lo];
        // 定义两个指针，分别指向待切分元素的最小索引处和最大索引处的下一个位置
        int left = lo ;
        int right = hi + 1;
         while (true){
             // 先从右往左扫描 移动right指针，找到一个比分界值小的元素值停止
             while(less(key,a[--right])){
                 if (right == lo){
                     break;
                 }

             }
             // 从左往右扫描 移动left指针，找到一个比分界值大的元素停止
             while(less(a[++left],key)){
                 if (left == hi){
                     break;
                 }

             }
             // 判断 left >= right，如果是，则证明元素扫面完成，如果不是，交换数据即可
             if (left >= right)
             {
                 break;
             }
             else {
                 exch(a,left,right);
             }
         }
         // 交换分界值
         exch(a,lo,right);
        return right; //返回的是分组的分界值索引,分界值位置变换后的索引
    }

    // 判断v是否小于w
    private static boolean less(Comparable v ,Comparable w){
        return v.compareTo(w)<0;
    }

    // 交换数据
    private static void exch(Comparable[] a,int i,int j){
        Comparable temp ;
        temp = a[i];
        a[i] = a[j];
        a[j] = temp;
    }
}
