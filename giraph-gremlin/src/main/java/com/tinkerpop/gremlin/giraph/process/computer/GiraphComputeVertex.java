package com.tinkerpop.gremlin.giraph.process.computer;

import com.tinkerpop.gremlin.giraph.Constants;
import com.tinkerpop.gremlin.giraph.process.computer.util.ConfUtil;
import com.tinkerpop.gremlin.giraph.process.computer.util.KryoWritable;
import com.tinkerpop.gremlin.giraph.process.computer.util.RuleWritable;
import com.tinkerpop.gremlin.giraph.structure.io.EmptyOutEdges;
import com.tinkerpop.gremlin.process.computer.VertexProgram;
import com.tinkerpop.gremlin.structure.Graph;
import com.tinkerpop.gremlin.structure.io.kryo.KryoReader;
import com.tinkerpop.gremlin.structure.io.kryo.KryoWriter;
import com.tinkerpop.gremlin.structure.util.wrapped.WrappedVertex;
import com.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import com.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex;
import org.apache.giraph.graph.Vertex;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 */
public final class GiraphComputeVertex extends Vertex<LongWritable, Text, NullWritable, KryoWritable> implements WrappedVertex<TinkerVertex> {

    //TODO: Dangerous that the underlying TinkerGraph Vertex can have edges written to it.

    private static final String VERTEX_ID = Graph.System.system("giraph.gremlin.vertexId");
    private VertexProgram vertexProgram;
    private TinkerVertex tinkerVertex;
    private GiraphMemory memory;

    public GiraphComputeVertex() {
    }

    public GiraphComputeVertex(final TinkerVertex tinkerVertex) {
        this.tinkerVertex = tinkerVertex;
        this.tinkerVertex.graph().variables().set(VERTEX_ID, this.tinkerVertex.id());
        this.initialize(new LongWritable(Long.valueOf(this.tinkerVertex.id().toString())), this.deflateTinkerVertex(), EmptyOutEdges.instance());
    }

    public TinkerVertex getBaseVertex() {
        return this.tinkerVertex;
    }

    protected long getWorkerId() {
        return ((GiraphWorkerContext) this.getWorkerContext()).getWorkerId();
    }

    @Override
    public void compute(final Iterable<KryoWritable> messages) {
        if (null == this.tinkerVertex)
            inflateTinkerVertex();
        if (null == this.vertexProgram)
            this.vertexProgram = VertexProgram.createVertexProgram(ConfUtil.makeApacheConfiguration(this.getConf()));
        if (null == this.memory)
            this.memory = new GiraphMemory(this, this.vertexProgram);

        if (!(Boolean) ((RuleWritable) this.getAggregatedValue(Constants.GREMLIN_GIRAPH_HALT)).getObject())
            this.vertexProgram.execute(this.tinkerVertex, new GiraphMessenger(this, messages), this.memory);  // TODO provide a wrapper around TinkerVertex for Edge and non-ComputeKeys manipulation
        else if (this.getConf().getBoolean(Constants.GREMLIN_GIRAPH_DERIVE_MEMORY, false)) {
            final Map<String, Object> memoryMap = new HashMap<>(this.memory.asMap());
            memoryMap.put(Constants.SYSTEM_ITERATION, this.memory.getIteration() - 1);
            this.tinkerVertex.singleProperty(Constants.MEMORY_MAP, memoryMap);
        }
    }

    ///////////////////////////////////////////////

    private Text deflateTinkerVertex() {
        try {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final KryoWriter writer = KryoWriter.build().create();
            writer.writeGraph(bos, this.tinkerVertex.graph());
            bos.flush();
            bos.close();
            return new Text(bos.toByteArray());
        } catch (final IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void inflateTinkerVertex() {
        try {
            final ByteArrayInputStream bis = new ByteArrayInputStream(this.getValue().getBytes());
            final KryoReader reader = KryoReader.build().create();
            final TinkerGraph tinkerGraph = TinkerGraph.open();
            reader.readGraph(bis, tinkerGraph);
            bis.close();
            this.tinkerVertex = (TinkerVertex) tinkerGraph.v(tinkerGraph.variables().get(VERTEX_ID).get());
        } catch (final Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}