package GoBackN_BJTU;


public class RdtProto {
    private enum RdtState {RDT_STATE_WAIT};
    private enum RdtEvent {RDT_EVENT_SEND, RDT_EVENT_ACK_RECV, RDT_EVENT_TIMEOUT};

    private final int  RDT_HEAD_LEN = 8;
    private static final int MAX_RDT_PKT_SIZE = 1440;
    private UdtChannel.UdtEndType commEndType;
    UdtChannel udtChannel;
    int base = 1;
    int nextSeqNum = 1;
    RdtState rdtState = RdtState.RDT_STATE_WAIT;


    public RdtProto(UdtChannel.UdtEndType endType) {
        commEndType = endType;
        udtChannel = new UdtChannel(endType);
        //udtChannel.setBandwidth(1); //设置带宽为1Mbps
        //udtChannel.setLossRatio(3); //设置丢包率为3%
        //udtChannel.setBRT(0.5);     //设置误比特率为万分之0.5
        //udtChannel.setPropDelay(5,15);
    }

    public void openChannel(String peerIpAddr){
        udtChannel.setReceiverAddress(peerIpAddr);
        udtChannel.open();
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

    private void rdtSendAction(byte[] data, int len) {
        udtChannel.udtSend(data, len);
    }

    private void rdtRecvAckAction(byte[] packet, int len) {
        System.out.println("ACK");
    }

    private void rdtTimeoutAction() {
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
        int len = udtChannel.udtRecv(buffer);
        //。。。。。
        return len;
    }



}
