package com.xw.learning.dataStructure;

import com.xw.learning.dataStructure.Java.Factorial;
import com.xw.learning.dataStructure.compare.SortCompare;
import com.xw.learning.dataStructure.sort.Bubble;
import com.xw.learning.dataStructure.sort.Insertion;
import com.xw.learning.dataStructure.sort.Select;
import com.xw.learning.dataStructure.sort.Shell;
import com.xw.learning.tools.Tools;

import java.util.Arrays;

/**
 * @program: LearningDemo
 * @description: 测试类
 * @author: authorName
 * @create: 2021-04-16 17:28
 **/
public class SortTest {
    static Integer[] a = {3,4,7,11,2,5,6,10,8,9,1};

    static Integer[] b = {3,4,7,5,2,5,6,8,9,1};
    static Integer[] c;
    public static void onStart(){
//        long index = Factorial.testFactorial(10);
//        Tools.log("index = " + index);
    }

    private static void loga(){
//        Bubble.sort(a);
//        Select.scrt(a);
//        Insertion.sort(a);
    }

    private static void logb(){
//        Shell.sort(b);
    }

    private static void logc(){
        try {
            c = SortCompare.getInteager();
        } catch (Exception e) {
            e.printStackTrace();
        }
        //SortCompare.testShell(c);
        //SortCompare.testInsertion(c);
        //Tools.log("c = "+ Arrays.toString(c));
    }
}
