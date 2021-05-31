package com.xw.learning.dataStructure.Java;

/**
 * @program: Learning
 * @description: 递归
 * @author: authorName
 * @create: 2021-04-29 15:11
 **/
public class Factorial {
    // 求n的阶乘
    public static long testFactorial(int n){
        if ( n == 1){
            return 1;
        }
        return n*testFactorial(n-1);
    }
}
