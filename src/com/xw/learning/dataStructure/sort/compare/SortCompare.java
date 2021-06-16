package com.xw.learning.dataStructure.sort.compare;

import com.xw.learning.dataStructure.sort.Insertion;
import com.xw.learning.dataStructure.sort.Merge;
import com.xw.learning.dataStructure.sort.Shell;
import com.xw.learning.tools.Tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * @program: Learning
 * @description: 希尔排序测试用例
 * @author: authorName
 * @create: 2021-04-28 17:39
 **/
public class SortCompare {
    // 获取到数组
    public static Integer[] getInteager() throws Exception{
        // 1 创建一个ArrayList集合，保存读取出来的整数
        ArrayList<Integer> list = new ArrayList<>();
        // 2 创建缓存读取留BufferedReader，读取数据，并存储到ArrayList中
        BufferedReader reader = new BufferedReader(new InputStreamReader(SortCompare.class.getClassLoader().getResourceAsStream("reverse_arr.txt")));
        String line = null;
        while((line = reader.readLine()) != null){
            // line 是字符串，需要转换成Integer
            int i = Integer.parseInt(line);
            list.add(i);
        }
        reader.close();
        // 3 吧ArrayList集合转成数组
        Integer[] a = new Integer[list.size()];
        list.toArray(a);
        // 4 调用测试代码完成测试
        return a;
    }


    // 测试希尔排序
    public static void testShell(Integer[] a){
        // 1 获取执行之前的时间
        long start = System.currentTimeMillis();
        // 2 执行算法代码
        Shell.sort(a);
        // 3 获取执行之后的时间
        long end = System.currentTimeMillis();
        // 4 获取程序执行的时间并输出
        long time = end - start;
        Tools.log("希尔排序所用时间 time = " + time+" 毫秒");
    }

    // 测试插入排序
    public static void testInsertion(Integer[] a){
        // 1 获取执行之前的时间
        long start = System.currentTimeMillis();
        // 2 执行算法代码
        Insertion.sort(a);
        // 3 获取执行之后的时间
        long end = System.currentTimeMillis();
        // 4 获取程序执行的时间并输出
        long time = end - start;
        Tools.log("插入排序所用时间 time = " + time+" 毫秒");
    }

    //测试归并排序
    public static void testMerge(Integer[] a){
        // 1 获取执行之前的时间
        long start = System.currentTimeMillis();
        // 2 执行算法代码
        Merge.sort(a);
        // 3 获取执行之后的时间
        long end = System.currentTimeMillis();
        // 4 获取程序执行的时间并输出
        long time = end - start;
        Tools.log("归并排序所用时间 time = " + time+" 毫秒");
    }
}
