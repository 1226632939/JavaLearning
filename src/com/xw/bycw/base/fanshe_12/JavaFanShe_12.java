package com.bycw.base.fanshe_12;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class JavaFanShe_12 {

    public  static void testRef(){
        // 运行时候类型描述的一个实例，包含了类的藐视（方法（Method），字段/数据成员（Filed））;
        // 能够实例化我们的对象实例；
        Class<?> cls = GameA.class;

        // 不用知道具体类型，也可以调用里面的函数和方法
        try {
            // step1：构建一个累的实例；
            // new 一个Class类的描述所对应的类的实例
            Object obj = (Object)cls.newInstance();

            // step2：从类的描述实例里面获取方法对象；
            // 类的实例.成员方法 --> 类的实例传入到方法里面，this；
            // Method s = cls.getMethod("test"); //返回public方法
            // Method[] ms = cls.getMethods(); //返回所有的public方法
            Method m = cls.getDeclaredMethod("test"); //成员函数对象
            // Method[] ms = cls.getDeclaredMethods(); //返回一个数组，数组包含了类里面所有的方法；
            // step3：invoke：方法+对象实例 调用这个方法
            m.invoke(obj);
            // end

            Field f = cls.getDeclaredField("a");
            f.set(obj,300);
            System.out.print("参数值为："+((GameA)obj).a+";\n");
        }catch (Exception e){

        }

    }
}
class GameA{
    public void test(){
        System.out.print("GameA test;\n");
    }
    public int a = 0;
    public int b = 10;
}