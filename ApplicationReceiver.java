/**
 * ApplicationReceiver.java
 * Author: Yantao SUN
 * Date: 2019/10/26
 * Version: V1.0
 * All rights Reserved, Designed By BJTUs
 **/
package GoBackN_BJTU;

public class ApplicationReceiver {
    private static final int MAX_BLOCK_SIZE = 1440;
    private static RdtProto rdtProto;
    private static String LOCAL_IP = "127.0.0.1";
//    private static UdtChannel udtChannel;

    private static void recvData() {
        byte[] data = new byte[MAX_BLOCK_SIZE];

        while (true) {
            int len = rdtProto.rdtRecv(data);
            //int len = udtChannel.udtRecv(data);
            int id = data[1] * 128 + data[0];
            System.out.println("Receive a packet, id = " + id + ", size = " + len);
            byte[] packet = new byte[3];
            packet[0] = data[0];
            packet[1] = packet[1];
            packet[2] = -1;
            rdtProto.rdtSend(packet, 3);
            //udtChannel.udtSend(packet, 3);
            System.out.println("send a ack");
        }
    }

    public static void main(String[] args) {
//        udtChannel = new UdtChannel(UdtChannel.UdtEndType.UDT_RECEIVER);
//        udtChannel.open();
        rdtProto = new RdtProto(UdtChannel.UdtEndType.UDT_RECEIVER);
        rdtProto.openChannel(LOCAL_IP);

        recvData();

        rdtProto.closeChannel();
//        udtChannel.close();
        System.out.println("Finished!");
    }
}
