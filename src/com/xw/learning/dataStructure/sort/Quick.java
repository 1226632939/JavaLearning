package com.xw.learning.dataStructure.sort;
/**
 * @program: Learning
 * @description: 快速排序
 * @author:
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

        return -1; //返回的是分组的分界值索引,分界值位置变换后的索引
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
