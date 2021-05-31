package com.xw.learning.dataStructure.sort;

import com.xw.learning.tools.Tools;

import java.util.Arrays;

/**
 * @program: Learning
 * @description: 希尔排序
 * @author: authorName
 * @create: 2021-04-28 17:12
 **/
public class Shell {

    public static void sort(Comparable[] a){
        // 1.根据数组a的长度，确定增长量h的值
        int h = 1;
        while (h<a.length/2){
            h = 2*h+1;
        }
        // 2.希尔排序
        while (h>=1){
            // 排序
            // 2.1 找到待插入的元素
            for (int i = h;i<a.length;i++){
                // 2.2 把待插入的元素插入到有序数列中
                for (int j = i;j >= h;j-=h){
                    // 待插入的元素是a[j],比较a[j]合a[j-h]
                    if (greater(a[j-h],a[j])){
                        // 交换元素
                        exch(a,j-h,j);
                    }else{
                        // 待插入元素已经找到了合适的位置，结束循环
                        // Tools.log(Arrays.toString(a));
                        break;
                    }
                }
            }
            // 减少h的值
            h = h/2;
        }
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
