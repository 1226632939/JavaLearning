package com.java.yanghui;

/*
 * 使用二维数组打印一个 10 行杨辉三角
 *
 * 1. 第一行有1个元素，第n行有n个元素
 * 2. 每一行的第一个元素和最后一个元素都是1
 * 3. 从第三行开始，对于第一个元素合最后一个元素和即：yanghui[i][j] = yanghui[i - 1][j - 1]+yanghui[i -1][j];
 */
public class YangHuiTest {

    public static void init(){
        yangHui();
    }

    public static void yangHui(){
        // 1. 声明并初始化二维数组
        int [][] yanghui = new int [10][]; // 列上的值需要计算赋值 所以在声明的时候不需要声明列有多长
        // 2. 给数组的元素复制
        for(int i = 0; i < yanghui.length; i++){
            yanghui[i] = new int [i + 1];
            // 2.1. 设置首末两个数的数值为1
            yanghui[i][0] = yanghui[i][i] = 1;      // 简写 yanghui[i][0] = 1; yanghui[i][i] = 1;
            // 2.2. 给每行非首末元素复制
            if (i > 1){ // 可省略 i = 0 时循环不会进入； i = 1 时小于j同样进不去
                for (int j = 1; j < yanghui[i].length - 1; j++){
                    yanghui[i][j] = yanghui[i - 1][j - 1]+yanghui[i -1][j];
                }
            }
        }
        // 3. 遍历二维数组
        for (int i = 0; i < yanghui.length; i++){
            for (int j = 0; j < yanghui[i].length; j++){
                System.out.print(yanghui[i][j]+"   ");
            }
            System.out.println();
        }
    }
}
