package com.xw.learning.dataStructure.sort;

import com.xw.learning.tools.Tools;

import java.util.Arrays;

/**
 * @program: Learning
 * @description: 选择排序
 * @author: authorName
 * @create: 2021-04-28 14:13
 **/
public class Select {

    /**
     * 数据比较次数 ((N-1)+1)*(N-1)/2 = N^2/2-N/2
     * 数据交换次数 N-1
     * 时间复杂度 N^2/2-N/2+(N-1) = N^2/2+N/2-1 根据大O推导法则 O(N^2)
     * */
    public static void scrt(Comparable[] a){
        for (int i = 0;i < a.length -2; i++){
            int minIndex = i;

            for (int j = i+1;j<a.length ;j++){
                Tools.log(" 第"+j+"次循环 a[j-1] : " + a[j-1] + " a[j] : " + a[j]);
                if (greater(a[minIndex],a[j])){
                    minIndex = j;
                }
            }
            exch(a,i,minIndex);
            Tools.log(" 当前数组循环第 " + i + " 次  值为 : " + Arrays.toString(a) + "\n");
        }
        Tools.log(Arrays.toString(a));
    }

    private static boolean greater(Comparable v, Comparable y){
        return v.compareTo(y)>0;
    }

    private static void exch (Comparable[] a , int i,int j){
        Comparable temp;
        temp = a[i];
        a[i] = a[j];
        a[j] = temp;

    }
}
