package com.xw.learning.dataStructure.sort;

import com.xw.learning.tools.Tools;

import java.util.Arrays;

/**
 * @program: LearningDemo
 * @description: 冒泡排序
 * @author: authorName
 * @create: 2021-04-16 16:53
 **/
public class Bubble {

    /**
     对数组内的元素进行排序
     */
    public static void sort(Comparable[] a){
        for (int i = a.length - 1; i > 0 ; i--){
            for (int j = 0; j < i ; j++){
                //比较索引j和索引j+1处的值
                if (greater(a[j],a[j+1])){
                    exch(a,j,j+1);
                }
            }
        }
        Tools.log(Arrays.toString(a));
    }
    /**
     比较V元素是否大于W元素
     **/
    private static boolean greater(Comparable v , Comparable w){
        return v.compareTo(w)>0;
    }
    /**
     交换a数组中，索引i和索引j处的值
     **/
    public static void exch(Comparable[] a,int i,int j){
        Comparable temp ;
        temp = a[i];
        a[i] = a[j];
        a[j] = temp;
    }
}
