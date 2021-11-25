package GoBackN_BJTU;


import java.util.Timer;
import java.util.TimerTask;

public class RdtProto {
    private enum RdtState {RDT_STATE_WAIT};
    private enum RdtEvent {RDT_EVENT_SEND, RDT_EVENT_ACK_RECV, RDT_EVENT_TIMEOUT};
    private Timer timer = new Timer();

    private final int  RDT_HEAD_LEN = 8;
    private static final int MAX_RDT_PKT_SIZE = 1440;
    private UdtChannel.UdtEndType commEndType;
    UdtChannel udtChannel;
    int base = 1;
    int nextSeqNum = 1;
    int expectedNum = 1;
    int N = 7;
    RdtState rdtState = RdtState.RDT_STATE_WAIT;
    boolean IsTimeOutInProcess;


    public RdtProto(UdtChannel.UdtEndType endType) {
        commEndType = endType;
        udtChannel = new UdtChannel(endType);
        udtChannel.setBandwidth(5); //设置带宽为1Mbps
        udtChannel.setLossRatio(1); //设置丢包率为3%
        udtChannel.setBRT(0.1);     //设置误比特率为万分之0.5
        udtChannel.setPropDelay(1,5);
    }

    public void openChannel(String peerIpAddr){
        udtChannel.setReceiverAddress(peerIpAddr);
        udtChannel.open();
        // 接受确认的线程，仅需发送方开启。
        if(commEndType == UdtChannel.UdtEndType.UDT_SENDER){
            Thread ackRecvThread = new Thread(new AckRecvThread());
            ackRecvThread.start();
        }
    }
    public void closeChannel(){
        udtChannel.close();
    }

    void sender_schedule(RdtEvent event, byte[] data, int len) {
        switch(rdtState) {
            case RDT_STATE_WAIT:
                if (event == RdtEvent.RDT_EVENT_SEND) {
                    rdtSendAction(data, len);
                } else if (event == RdtEvent.RDT_EVENT_ACK_RECV) {
                    rdtRecvAckAction(data, len);
                } else if  (event == RdtEvent.RDT_EVENT_TIMEOUT) {
                    rdtTimeoutAction();
                } else {
                    System.out.println("Error event received in state RDT_STATE_WAIT by RdtProto Schedule!");
                }
                break;
            default:
                System.out.println("Error event received by RdtProto Schedule!");
        }
    }

    // Timer 是一个定时器，启动定时器，规定时间后就会执行run
    class Timeout extends TimerTask {
        Timer timer;
        public Timeout(Timer timer){
            this.timer = timer;
        }

        @Override
        public void run() {
            sender_schedule(RdtEvent.RDT_EVENT_TIMEOUT, new byte[MAX_RDT_PKT_SIZE], MAX_RDT_PKT_SIZE);
        }
    }

    // 等待
    public void refuse(byte[] buffer, int len) throws InterruptedException {
        Thread.sleep(20);
    }


    private void rdtSendAction(byte[] data, int len) {
        // 如果是 receiver 说明是发送ACK，不需要任何检验，直接发送即可。
        if(commEndType == UdtChannel.UdtEndType.UDT_RECEIVER){
            udtChannel.udtSend(data, len);
        }else {
            // 发送方，判断是否发送了，即是否进入了第一层if语句，
            // 如果没有发送，则反复执行，否则这些数据会丢失。应当保证这些数据被发送了。
            // 参考FSM图，易解。
            boolean isSendOK = false;
            while (!isSendOK) {

                if (!IsTimeOutInProcess && nextSeqNum < base + N) {
                    udtChannel.udtSend(data, len);
                    isSendOK = true;
                    if (base == nextSeqNum) {
                        start_timer();
                    }
                    nextSeqNum++;
                } else {
                    try {
                        refuse(data, len);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }// end if

            }// end while
        }
    }

    private void rdtRecvAckAction(byte[] packet, int len) {
        int id = packet[1] * 128 + packet[0];
        System.out.println("ACK: " + id);
        if(id >= base){
            base = id;
            System.out.println("base: " + base + " nextSeqNum: " + nextSeqNum);
            if(base == nextSeqNum)
                stop_timer();
            else
                start_timer();
        }
    }

    // 设置发送数据的长度范围
    private static int genDataBlockSize() {
        return 500 +  (int)(Math.random() * (1000 - 500));
    }

    private void rdtTimeoutAction() {
        try {
            int next = nextSeqNum;
            int b = base;
            // 执行timeout的flag，由于 rdtsend 也要使用udtChannel，
            // 为了防止二者同时占用，因此需要设置flag：IsTimeOutInProcess
            IsTimeOutInProcess = true;
            // 发送任务结束后不需再执行重传
            for (int i = b; i < next; i++) {
                byte[] packet = new byte[MAX_RDT_PKT_SIZE];
                packet[0] = (byte) (i % 128);
                packet[1] = (byte) (i / 128);
                int datasize = genDataBlockSize();
                make_pkt(packet, datasize);
                // 下面这句用于测试，与GBN无关
                packet[packet.length - 4] = (byte) 233;
                udtChannel.udtSend(packet, datasize);
                if (i == b)
                    start_timer();

                IsTimeOutInProcess = false;
                System.out.println("Retransmit packet: " + b + " to " + (nextSeqNum-1));
            }
            Thread.sleep(N * 25);

        }catch (Exception e){
            e.printStackTrace();
        }

    }

    public void make_pkt(byte[] data, int length){
        long checksum = 0;
        for(int i = 2; i < length; i++)
            checksum += data[i];
        data[data.length-1] = (byte) checksum;
        // 数据的长度，用两位byte存储。
        data[data.length-3] = (byte) (length % 128);
        data[data.length-2] = (byte) (length / 128);
    }

    public boolean isCorrupted(byte[] packet){
        int length = packet[packet.length - 2] * 128 + packet[packet.length - 3];
        long checksum = 0;
        for(int i = 2; i < length; i++)
            checksum += packet[i];
        if((byte)checksum == packet[packet.length - 1])
            return false;
        else return true;
    }

    public void start_timer(){
        // 定时器启动，时间到后启动相应的线程。
        timer.schedule(new Timeout(timer), 20);
    }

    public void stop_timer(){
        timer.cancel();
        // timer是一次性的，cancel后需再建一个。
        timer = new Timer();
    }

    public void rdtSend(byte[] buffer, int len){
        sender_schedule(RdtEvent.RDT_EVENT_SEND, buffer,len);
    };

    class AckRecvThread implements Runnable {
        byte[] buffer = new byte[MAX_RDT_PKT_SIZE];
        @Override
        public void run() {
           while (true) {
               int len = udtChannel.udtRecv(buffer);
               sender_schedule(RdtEvent.RDT_EVENT_ACK_RECV, buffer, len);
           }
        }
    }

    public int rdtRecv(byte[] buffer) {
        udtChannel.udtRecv(buffer);

        // 测试语句，与GBN无关。
        if(buffer[buffer.length-4] == (byte) 233)
            System.out.println("retransmit received!");

        int len = buffer[buffer.length - 2] * 128 + buffer[buffer.length - 3];
        int seq = buffer[1] * 128 + buffer[0];
        if (!isCorrupted(buffer) && seq == expectedNum){
            expectedNum++;
            byte[] packet = new byte[3];
            packet[0] = buffer[0];
            packet[1] = buffer[1];
            packet[2] = -1;
            udtChannel.udtSend(packet, 3);
        }else {
            byte[] packet = new byte[3];
            packet[0] = (byte) (expectedNum % 128);
            packet[1] = (byte) (expectedNum / 128);
            packet[2] = -1;
            udtChannel.udtSend(packet, 3);
            rdtSend(packet, 3);
        }
        System.out.println("expected: " + expectedNum);
        return len;
    }

}
