package com.xw.learning.dataStructure.sort;

/**
 * @program: Learning
 * @description: 递归排序
 * @author: authorName
 * @create: 2021-04-29 15:23
 **/
public class Merge {

    private static Comparable[] assist;

    // 对数组进行排序
    public static void sort(Comparable[] a){
        // 1 初始化辅助数字assist
        assist = new Comparable[a.length];
        // 2 定义一个lo变量合hi变量，分别记录数组最小的索引和最大的索引
        int lo = 0;
        int hi = a.length - 1;
        // 3 调用sort重载方法完成数组a中，从索引lo到索引hi的排序
        sort(a,lo,hi);
    }
    // 对数组a中从索引lo到索引hi之间的元素进行排序
    private static void sort(Comparable[] a,int lo,int hi){
        // 安全性校验
        if (hi <= lo){
            return;
        }
        // 对lo到hi之间的数据分两组
        int mid = lo+(hi-lo)/2;
        // 分别对每一组数据排序
        sort(a,lo,mid);
        sort(a,mid+1,hi);
        // 再把两个组中的数据进行归并
        merge(a,lo,mid,hi);
    }

    // 从素哟因lo到索引mid为一个子组，从索引mid+1到索引hi为另外一个子组，
    // 把数组a中的两个子组的数据合并成一个有序的大组(从索引lo到索引hi)
    private static void merge(Comparable[]a,int lo,int mid,int hi){

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
