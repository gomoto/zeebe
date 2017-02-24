package org.camunda.tngp.broker.clustering.gossip.message;

import static org.camunda.tngp.clustering.gossip.ProbeEncoder.*;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.broker.clustering.channel.Endpoint;
import org.camunda.tngp.clustering.gossip.MessageHeaderDecoder;
import org.camunda.tngp.clustering.gossip.MessageHeaderEncoder;
import org.camunda.tngp.clustering.gossip.ProbeDecoder;
import org.camunda.tngp.clustering.gossip.ProbeEncoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class ProbeRequest implements BufferReader, BufferWriter
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ProbeDecoder bodyDecoder = new ProbeDecoder();

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ProbeEncoder bodyEncoder = new ProbeEncoder();

    protected final Endpoint target = new Endpoint();

    public Endpoint target()
    {
        return target;
    }

    public ProbeRequest target(final Endpoint target)
    {
        this.target.wrap(target);
        return this;
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength() +
                hostHeaderLength() +
                target.hostLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        bodyEncoder.wrap(buffer, offset)
            .port(target.port())
            .putHost(target.getHostBuffer(), 0, target.hostLength());
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        final int hostLength = bodyDecoder.hostLength();

        target.port(bodyDecoder.port());
        target.hostLength(hostLength);
        bodyDecoder.getHost(target.getHostBuffer(), 0, hostLength);
    }

    public void reset()
    {
        target.reset();
    }

}
