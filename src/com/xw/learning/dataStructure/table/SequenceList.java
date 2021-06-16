package com.xw.learning.dataStructure.table;

import com.xw.learning.tools.Tools;

import java.util.Iterator;

/**
 * @program: Learning
 * @description: 顺序表
 * @author: authorName
 * @create: 2021-04-28 16:04
 **/
public class SequenceList<T> implements Iterable<T>{

    private T[] eles; // 存储元素的数组
    private int N; //当前线性表长度

    // 创建容量为capactiy的SequenceList
    public SequenceList(int capactiy){
        // 初始化数组
        this.eles =(T[]) new Object[capactiy];
        // 初始化长度
        this.N = 0;
    }

    // 空置线性表
    public void clear(){
        this.N = 0;
    }

    //  判断线性表是否为空，是返回true，否返回false
    public boolean isempaty(){
        return N == 0;
    }

    // 获取线性表中元素的个数
    public int length(){
        return N;
    }

    // 读取并返回线性表中的第i个值
    public T get(int i){
       return eles[i];
    }

    // 向线性表中添加一个元素
    public void insert(T t){
        if (N==eles.length){
            resize(2 * eles.length);
        }
        eles[N++] = t;
    }

    // 在线性表的第i个元素之前插入一个值为 T 的数据元素
    public void insert(int i,T t){
        if (N==eles.length){
            resize(2 * eles.length);
        }
        // 先把i索引处及其后面的元素依次向后移动一位
        for (int index = N;index > i;index--){
            eles[index] = eles[index -1];
        }
        // 再把t元素放到i索引出
        eles[i] = t;
        N++;
    }

    // 删除并返回线性表中第i个数据元素
    public T remove(int i){
        // 记录索引i处的值
        T current = eles[i];
        // 让索引i后面的元素依次向前移动一位即可
        for (int index = i; index < N-1; index++){
            eles [index] = eles[index + 1];
        }
        // 元素个数-1
        N = N--;
        if (N<eles.length/4)
        {
            resize(eles.length/2);
        }
        return current;
    }

    // 返回线性表中首次出现的指定的数据元素的位序号，若不存在，则返回-1
    public int indexOf(T t){
        for (int i = 0; i < N; i++){
            if (eles[i].equals(t)){
                return i;
            }
        }
        return -1;
    }

    // 重新定制顺序表大小
    // 根据参数newSize,充值eles的大小
    public void resize(int newSize){
        // 定义一个临时数组，指向原数组
        T[] temp = eles;
        // 创建新数组
        eles = (T[]) new Object[newSize];
        // 把原数组的数据拷贝到新数组即可
        for (int i = 0;i<N-1;i++){
            eles[i] = temp[i];
        }
    }

    // 遍历
    @Override
    public Iterator<T> iterator() {
        return new Siterator();
    }

    private class Siterator implements Iterator{
        private int cusor;
        public Siterator(){
            this.cusor = 0;
        }

        @Override
        public boolean hasNext() {
            return cusor < N;
        }

        @Override
        public Object next() {
            return eles[cusor++];
        }
    }



}
