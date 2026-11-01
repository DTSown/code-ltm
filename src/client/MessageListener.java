package client;

import common.Message;

public interface MessageListener {
    void onMessage(Message message);
}
