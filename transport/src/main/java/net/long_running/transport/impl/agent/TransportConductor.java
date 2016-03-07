package net.long_running.transport.impl.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.long_running.dispatcher.AsyncCompletionCallback;
import net.long_running.transport.ClientChannel;
import net.long_running.transport.ServerSocketBinding;
import net.long_running.transport.impl.BaseChannelImpl;
import net.long_running.transport.impl.ClientChannelImpl;
import net.long_running.transport.impl.ServerSocketBindingImpl;
import net.long_running.transport.impl.TransportContext;
import net.long_running.transport.impl.media.AcceptTransportPoller;
import net.long_running.transport.impl.media.ConnectTransportPoller;
import uk.co.real_logic.agrona.concurrent.Agent;
import uk.co.real_logic.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class TransportConductor implements Agent, Consumer<TransportConductorCmd>
{
    protected final ManyToOneConcurrentArrayQueue<TransportConductorCmd> cmdQueue;
    protected final ManyToOneConcurrentArrayQueue<SenderCmd> toSenderCmdQueue;
    protected final ManyToOneConcurrentArrayQueue<ReceiverCmd> toReceiverCmdQueue;

    protected final ConnectTransportPoller connectTransportPoller;
    protected final AcceptTransportPoller acceptTransportPoller;

    protected Map<ClientChannelImpl, AsyncCompletionCallback<ClientChannel>> connectCallbacks = new HashMap<>();

    public TransportConductor(TransportContext context)
    {
        cmdQueue = context.getConductorCmdQueue();
        toSenderCmdQueue = context.getSenderCmdQueue();
        toReceiverCmdQueue = context.getReceiverCmdQueue();
        connectTransportPoller = new ConnectTransportPoller(this);
        acceptTransportPoller = new AcceptTransportPoller(this);
    }

    @Override
    public int doWork() throws Exception
    {
        int workCount = 0;

        workCount += cmdQueue.drain(this);

        workCount += connectTransportPoller.pollNow();

        workCount += acceptTransportPoller.pollNow();

        return workCount;
    }

    @Override
    public String roleName()
    {
        return "client-conductor";
    }

    @Override
    public void accept(TransportConductorCmd t)
    {
        t.execute(this);
    }

    public ManyToOneConcurrentArrayQueue<TransportConductorCmd> getCmdQueue()
    {
        return cmdQueue;
    }

    public void onClientChannelConnected(final ClientChannelImpl channel)
    {
        connectTransportPoller.removeChannel(channel);

        onChannelOpened(channel);

        final AsyncCompletionCallback<ClientChannel> connectCallback = connectCallbacks.get(channel);
        if(connectCallback != null)
        {
            connectCallback.onComplete(null, channel);
        }
    }

    public void onChannelOpened(final BaseChannelImpl channel)
    {
        toReceiverCmdQueue.add((r) ->
        {
            r.registerChannel(channel);
        });

        toSenderCmdQueue.add((s) ->
        {
            s.registerChannel(channel);
        });
    }

    public void doConnectChannel(
            final ClientChannelImpl channel,
            AsyncCompletionCallback<ClientChannel> connectCallback)
    {
        try
        {
            channel.startConnect();
            connectTransportPoller.addChannel(channel);
            connectCallbacks.put(channel, connectCallback);
        }
        catch(Exception e)
        {
            connectCallback.onComplete(e, null);
        }
    }

    public void doBindServerSocket(
            final ServerSocketBindingImpl serverSocketBinding,
            final AsyncCompletionCallback<ServerSocketBinding> completionCallback)
    {
        try
        {
            serverSocketBinding.doBind();
            acceptTransportPoller.addServerSocketBinding(serverSocketBinding);
            completionCallback.onComplete(null, serverSocketBinding);
        }
        catch(Exception e)
        {
            completionCallback.onComplete(e, null);
        }

    }

}
