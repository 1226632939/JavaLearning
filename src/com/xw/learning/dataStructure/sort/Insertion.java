package com.xw.learning.dataStructure.sort;

import java.util.Arrays;

/**
 * @program: Learning
 * @description: 插入排序
 * @author: authorName
 * @create: 2021-04-28 16:04
 **/
public class Insertion {
    public static void sort(Comparable[] a){
        for (int i = 1;i<a.length;i++){
            int index = i;
            for (int j = i ;j > 0;j--){
                // 比较索引 j处的值和j-1处的值
                 if (greater(a[j-1],a[j])){
                     exch(a,j-1,j);
                }

            }
        }
    }
    /**
     比较V元素是否大于W元素
     **/
    private static boolean greater(Comparable v , Comparable w){
        boolean bool = v.compareTo(w)>0;
        System.out.println("gre ater  v : " + v + " w : " + w + " bool : " + bool);
        return bool;
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
