/**
 * UdtChannel.java
 * Package: GobackN_BJTU
 * Author: Yantao SUN
 * Date: 2019/10/26
 * Version: V1.0
 * All rights Reserved, Designed By BJTUs
 **/
package GoBackN_BJTU;

import java.io.IOException;
import java.net.*;
import java.util.LinkedList;
import java.util.Queue;

public class UdtChannel {

    private final int ONE_MILLION = 1000000;
    private final String LOCAL_IP = "127.0.0.1";
    private final long QUEUE_SIZE = 100;
    private final double MIN_BANDWIDTH = 0.1;
    private final double MAX_BANDWIDTH = 10;

    private final int receiverPort = 12345;
    enum UdtEndType {UDT_SENDER, UDT_RECEIVER};

    private InetAddress receiverIpAddress;
    private double lossRatio = 0.01;      //百分之一
    private double bitErrorRatio = 0.00005;   //万分之0.5
    private double propLowerDelay = 5.0; //ms
    private double propUpperDelay = 15.0; //ms
    private double bandwidth = 1.0;      //Mbps
    private DatagramSocket senderSocket;
    private DatagramSocket receiverSocket;
    private UdtEndType udtEndType;
    /*
     * LinkedList不是线程安全的。
     * 在本程序中，有两个线程同时访问packetQueue，理论上讲，应该使用互斥锁保护该队列。
     * 请同学们自己加锁。
     */
    private Queue<byte[]>  packetQueue = new LinkedList<byte[]>();
    private PacketSendThread packetSendThread = new PacketSendThread();

    private boolean stopSendThread = false;

    class PacketSendThread implements Runnable {
        @Override
        public void run() {
            long delay = 0;
            stopSendThread = false;
            long sendTime = System.currentTimeMillis();

            while (!stopSendThread) {
                long currentTime = System.currentTimeMillis();
                if (currentTime > sendTime) {
                    if (!packetQueue.isEmpty()) {
                        byte[] packet = packetQueue.remove();
                        if(!isDropPacket(packet.length)) {
                            corruptPacket(packet);
                            udpSend(packet);
                        }
                        delay = calcE2EDelay(packet.length);
                        sendTime = sendTime + delay;
                    } else {
                        sendTime = currentTime;
                        delay = 5;
                    }

                    try {
                        Thread.sleep(delay / 2);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }//end if
            }//end while
        }
    }

    private void startSendThread() {
        Thread t = new Thread(packetSendThread);
        t.start();
    }

    private void stopSendThread() {
        try {
            Thread.sleep(5);
            while(packetQueue.size() > 0) {
                Thread.sleep(5);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        stopSendThread = true;
    }

    public UdtChannel(UdtEndType endType)  {

        udtEndType = endType;
        try {
            receiverIpAddress=  InetAddress.getByName(LOCAL_IP);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        try {
            senderSocket = new DatagramSocket();
            if (udtEndType == UdtEndType.UDT_SENDER) {
                receiverSocket = new DatagramSocket(12346);
            }
            else {
                receiverSocket = new DatagramSocket(receiverPort);
            }

        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public void open()
    {
        startSendThread();
    }
    //关闭Channel
    public void close(){

        if (udtEndType == UdtEndType.UDT_SENDER) {
            stopSendThread();
            senderSocket.close();
        } else {
            receiverSocket.close();
        }
    }

   public void setReceiverAddress(String ipAddress) {
       try {
           receiverIpAddress = InetAddress.getByName(ipAddress);
       } catch (UnknownHostException e) {
           e.printStackTrace();
       }
   }

    public void setLossRatio(double lossRatio) {
        this.lossRatio = lossRatio / 100;
    }

    public void setBRT(double brt) {
        bitErrorRatio = brt / 10000;
    }

    void setPropDelay(double lower, float upper) {
        propLowerDelay = lower;
        propUpperDelay = upper;
    }

    void setBandwidth(double bandwidth){
        if (bandwidth < MIN_BANDWIDTH || bandwidth > MAX_BANDWIDTH) {
            return;
        }
        this.bandwidth = bandwidth;
    }

    //计算是否丢包
    private boolean  isDropPacket(int packetSize){  //packetSize 单位：byte
        if (Math.random() < lossRatio) {
            return true;
        }
        return false;
    }

    private void corruptPacket( byte[] packet){  //packetSize 单位：byte
        double packetErrorRatio = 1 - Math.pow(1 - bitErrorRatio, (packet.length * 8));

        //corrupt packet
        if (Math.random() < packetErrorRatio) {
            if (packet[1] == 0) {
                packet[0] = (byte)(-packet[0]);
            } else {
                packet[1] = (byte) (-packet[1]);
            }
            packet[2] = (byte)Math.random();
            packet[packet.length - 1] = (byte)Math.random();
        }
    }

    //计算端到端延迟 = 发送延迟 + 传播延迟，单位：毫秒, 忽略了排队和处理延迟等。
    private long calcE2EDelay(int packetSize) {
        double transmissionDelay = (packetSize * 8) / (bandwidth * ONE_MILLION);
        double propagationDelay = propLowerDelay +  Math.random() * (propUpperDelay - propLowerDelay);
        return (long)(transmissionDelay + propagationDelay);
    }

    private void udpSend(byte[] packet){
        int pip;
        if(udtEndType == UdtEndType.UDT_SENDER)
            pip = receiverPort;
        else
            pip = 12346;
        DatagramPacket dataPacket = new DatagramPacket(packet, packet.length, receiverIpAddress, pip);
        try {
            senderSocket.send(dataPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //返回值：是否发送成功
    public void udtSend(byte [] data, int length) {
        byte[] packet = new byte[length];
        System.arraycopy(data, 0, packet,0, length);
        while(true) {
            if (packetQueue.size() > QUEUE_SIZE) {
                try {
                    Thread.sleep(3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            packetQueue.add(packet);
            break;
        }
    }


    public int udtRecv(byte[] buffer)  {
        //新建数据包，会把后面收到的内容放到buffer字节数组里，最大长度为buffer.length
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        //此方法为阻塞方法,直到监听到数据包后才会往下执行，不然就一直等待，就像ServerSocket.accept()方法一样
        try {
            receiverSocket.receive(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return packet.getLength();
    }

}
