package org.camunda.tngp.broker.transport.cfg;

public class SocketBindingCfg
{
    public String host;
    public int port = -1;
    public int receiveBufferSize = -1;
    public long controlMessageRequestTimeoutInMillis = 10_000;
}
