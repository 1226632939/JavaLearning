package com.xw.learning.dataStructure.table.test;

import com.xw.learning.dataStructure.table.SequenceList;
import com.xw.learning.tools.Tools;
import sun.rmi.runtime.Log;

import javax.tools.Tool;

public class TableTest {
    public static void onStart(){
//        sequenceListTest();
        sequenceListTest2();
    }

    private static void sequenceListTest(){
        // 创建顺序表对象
        SequenceList<String> sl = new SequenceList<String>(10);
        // 测试插入
        sl.insert("RNG");
        sl.insert("IG");
        sl.insert("TES");
        sl.insert("OMG");
        sl.insert(1,"NBK");
        Tools.log("当前线性表中的元素个数为 ： " + sl.length());
        String log = "\n-----------------------------------------";
        for (String s:sl){
            log += "\n "+s;
        }
        log += "\n-----------------------------------------";
        Tools.log("当前表内数据 ： " + log);
        // 测试获取
        String getResult = sl.get(4);
        Tools.log("获取索引1处的结果为 ： " + getResult);
        // 测试删除
        String removeResult = sl.remove(0);
        Tools.log("删除的元素是 ： " + removeResult);
        // 测试清空
        sl.clear();
        Tools.log("清空后的线性表中的元素个数为 ： " + sl.length());
    }

    private static void sequenceListTest2(){
        SequenceList<String> sl = new SequenceList<String>(3);
        sl.insert("WBG");
        sl.insert("ACD");
        sl.insert("WWW");
        sl.insert("ACG");
        Tools.log("N = " + sl.length());
    }
}
