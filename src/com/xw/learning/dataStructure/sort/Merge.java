package com.xw.learning.dataStructure.sort;

/**
 * @program: Learning
 * @description: 归并排序
 * @author: authorName
 *          最终归并排序时间复杂度为 O(nlogn)；
 *          归并排序的缺点 ： 需要申请额外的数组空间，导致空间复杂度提升，是典型的以空间换时间的操作
 * @create: 2021-04-29 15:23
 **/
public class Merge {
    // 归并所需要的辅助数组
    private static Comparable[] assist;

    // 对数组进行排序
    public static void sort(Comparable[] a){
        // 1 初始化辅助数组 assist
        assist = new Comparable[a.length];
        // 2 定义一个lo变量合hi变量，分别记录数组最小的索引和最大的索引
        int lo = 0;
        int hi = a.length - 1;
        // 3 调用sort重载方法完成数组a中，从索引lo到索引hi的排序
        sort(a,lo,hi);
    }
    // 对数组a中从索引lo到索引hi之间的元素进行排序
    private static void sort(Comparable[] a,int lo,int hi){
        // 安全性校验
        if (hi <= lo){
            return;
        }
        // 对lo到hi之间的数据分两组
        int mid = lo + (hi - lo) / 2;  // lo = 5 hi = 9 mid = 7
         // 分别对每一组数据排序
        sort(a,lo,mid);
        sort(a,mid+1,hi);
        // 再把两个组中的数据进行归并
        merge(a,lo,mid,hi);
    }

    // 从素哟因lo到索引mid为一个子组，从索引mid+1到索引hi为另外一个子组，
    // 把数组a中的两个子组的数据合并成一个有序的大组(从索引lo到索引hi)
    private static void merge(Comparable[]a,int lo,int mid,int hi){
        // 定义三个指针
        int i = lo;
        int p1 = lo;
        int p2 = mid + 1;
        // 遍历 移动P1指针和p2指针，比较对应索引的值，找出小的那个，放到辅助数组对应索引处
        while (p1 <= mid && p2 <= hi){
            // 比较对应索引值
            if (less(a[p1],a[p2])){
                assist[i++] = a[p1++];
            }else {
                assist[i++] = a[p2++];
            }
        }
        // 遍历，如果p1的指针没有走完，那么顺序移动p1把对应的元素放到辅助数组对应索引处
        while (p1 <= mid){
            assist[i++] = a[p1++];
        }
        // 遍历，如果p2的指针没有走完，那么顺序移动p2把对应的元素放到辅助数组对应索引处
        while (p2 <= hi){
            assist[i++] = a[p2++];
        }
        // 把辅助数组中的元素拷贝到原数组中

        for (int index = lo ; index <= hi;index ++)
        {
            a[index] = assist[index];
        }

    }

    // 判断v是否小于w
    private static boolean less(Comparable v ,Comparable w){
        return v.compareTo(w)<0;
    }

    // 交换数据
    private static void exch(Comparable[] a,int i,int j){
        Comparable temp ;
        temp = a[i];
        a[i] = a[j];
        a[j] = temp;
    }
}
