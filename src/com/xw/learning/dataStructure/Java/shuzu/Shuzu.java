package com.java.shuzu;

public class Shuzu {
    public static void init (){
        String arr[] = new String[]{"AA","BB","CC","DD","QQ","WW","EE","RR","CC","ZZ","FF"};
        String str = "";
        for (int i = 0; i < arr.length; i++){
            str += arr[i]+" ";
        }

        // 数组翻转
//        for (int i = 0,j = arr.length-1; i < j; i++,j--){
//            String temp = arr[i];
//            arr[i] = arr[j];
//            arr[j] = temp;
//        }
        System.out.print("当前数组 ： " + str + ";\n");

        // 线性查找
        String dest = "BB";
        int index;
        boolean isFlag = true;
        for (int i = 0; i < arr.length; i++){
            if (arr[i].equals(dest)){
                index = i;
                isFlag = false;
                System.out.print("找到指定元素：" + dest + ",位置为：" + index + ";\n");
                break;
            }
        }
        if (isFlag){
            System.out.print("没有找到指定元素;\n");
        }
    }
}
