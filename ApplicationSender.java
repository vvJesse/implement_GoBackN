/**
 * ApplicationSender.java
 * Package: GobackN_BJTU
 * Author: Yantao SUN
 * Date: 2019/10/26
 * Version: V1.0
 * All rights Reserved, Designed By BJTUs
 **/
package GoBackN_BJTU;

public class ApplicationSender {

    private static final int MIN_BLOCK_SIZE = 50;
    private static final int MAX_BLOCK_SIZE = 1440;
    private static String LOCAL_IP = "127.0.0.1";

    private static int minBlockSize = MIN_BLOCK_SIZE;
    private static int maxBlockSize = MAX_BLOCK_SIZE;

//    private static UdtChannel udtChannel;
    private static RdtProto rdtProto;

    private static void setDataBlockSizeRange(int min, int max) {
        minBlockSize = min;
        maxBlockSize = max;
    }

    private static int genDataBlockSize() {
        return minBlockSize +  (int)(Math.random() * (maxBlockSize - minBlockSize));
    }

    private static void sendData() {
        byte[] data = new byte[MAX_BLOCK_SIZE];
        for (int k = 2; k < MAX_BLOCK_SIZE; k++) {
            data[k] = (byte)k;
        }

        setDataBlockSizeRange(500, 1000);
        int packet_num = 100;
        long beginTime = System.currentTimeMillis();
        for (int i = 1; i < packet_num; i++) {
            //生成报文编号
            data[0] = (byte) (i % 128);
            data[1] = (byte) (i / 128);
            int dataSize = genDataBlockSize();
            rdtProto.make_pkt(data, dataSize);
            rdtProto.rdtSend(data, dataSize);
            System.out.println("Send a packet, id = " + i + ", size = " + dataSize);
        }

        while (rdtProto.base < packet_num){
            // 全部发送后，也不一定全部收到，可能还要经过重传才能接收到。
            // 在这里，必须等待，否则就会执行后面的closechannel，一旦关闭了通道，
            // 后面的重传就不可能收到了。
            System.out.println(rdtProto.base);
            try {
                Thread.sleep(100);
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        long endTime = System.currentTimeMillis();
        System.out.println(endTime-beginTime);

    }

    public static void main(String[] args) {
        rdtProto = new RdtProto(UdtChannel.UdtEndType.UDT_SENDER);
        rdtProto.openChannel(LOCAL_IP);

        sendData();

        rdtProto.closeChannel();
        System.out.println("Finished!");
    }
}
