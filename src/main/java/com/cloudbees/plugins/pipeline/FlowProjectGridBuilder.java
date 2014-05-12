package com.cloudbees.plugins.pipeline;

import au.com.centrumsystems.hudson.plugin.buildpipeline.*;
import com.cloudbees.plugins.flow.BuildFlow;
import com.cloudbees.plugins.flow.FlowRun;
import com.cloudbees.plugins.flow.JobInvocation;
import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.util.AdaptedIterator;
import hudson.util.HttpResponses;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
//import jenkins.util.TimeDuration;
import org.jgrapht.DirectedGraph;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
//import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ProjectGridBuilder} based on the upstream/downstream relationship.
 *
 * @author Kohsuke Kawaguchi
 */
public class FlowProjectGridBuilder extends ProjectGridBuilder {
    private static final Logger LOGGER = Logger.getLogger("flow-builder");

    /**
     * Name of the first job in the grid, relative to the owner view.
     */
    private String firstJob;

    /**
     * @param firstJob Name of the job to lead the piepline.
     */
    @DataBoundConstructor
    public FlowProjectGridBuilder(String firstJob) {
        this.firstJob = firstJob;
    }

    /**
     * {@link au.com.centrumsystems.hudson.plugin.buildpipeline.ProjectGrid} that lays out things via upstream/downstream.
     */
    private static final class GridImpl extends DefaultProjectGridImpl {
        /**
         * Project at the top-left corner. Initiator of the pipeline.
         */
        private final AbstractProject<?, ?> start;

        /**
         * @param start The first project to lead the pipeline.
         */
        private GridImpl(AbstractProject<?, ?> start) {
            this.start = start;
            placeProjectInGrid(0, 0, ProjectForm.as(start));
        }

        /**
         * Function called recursively to place a project form in a grid
         *
         * @param startingRow    project will be placed in the starting row and 1st child as well. Each subsequent
         *                       child will be placed in a row below the previous.
         * @param startingColumn project will be placed in starting column. All children will be placed in next column.
         * @param projectForm    project to be placed
         */
        private void placeProjectInGrid(final int startingRow, final int startingColumn, final ProjectForm projectForm) {
            if (projectForm == null) {
                return;
            }

            int row = getNextAvailableRow(startingRow, startingColumn);
            set(row, startingColumn, projectForm);

//            final int childrensColumn = startingColumn + 1;
//            for (final ProjectForm downstreamProject : projectForm.getDependencies()) {
//                placeProjectInGrid(row, childrensColumn, downstreamProject);
//                row++;
//            }
        }



        /**
         * Factory for {@link Iterator}.
         */
        private final Iterable<BuildGrid> builds = new Iterable<BuildGrid>() {
//            @Override
            public Iterator<BuildGrid> iterator() {
                if (start == null) {
                    return Collections.<BuildGrid>emptyList().iterator();
                }

                final Iterator<? extends AbstractBuild<?, ?>> base = start.getBuilds().iterator();
                return new AdaptedIterator<AbstractBuild<?, ?>, BuildGrid>(base) {
                    @Override
                    protected BuildGrid adapt(AbstractBuild<?, ?> item) {
                        return new BuildGridImpl((FlowRun) item);
                    }
                };
            }
        };

        @Override
        public Iterable<BuildGrid> builds() {
            return builds;
        }

        @Override
        public int getColumns() {
            return 10;
        }

        @Override
        public int getRows() {
            return 5;
        }
    }

    public static final class FlowGraph {
        private DirectedGraph<JobInvocation,FlowRun.JobEdge> graph;
        private JobInvocation root;

        public FlowGraph(DirectedGraph<JobInvocation, FlowRun.JobEdge> graph) {
            this.graph = graph;
        }

        public FlowGraph(JobInvocation root, DirectedGraph<JobInvocation, FlowRun.JobEdge> graph) {
            this.root = root;
            this.graph = graph;
        }

        public Set<JobInvocation> getOutgoingDependencies(JobInvocation jobInvocation) {
            Set<JobInvocation> dependencies = new HashSet<JobInvocation>();
            for(FlowRun.JobEdge edge: graph.outgoingEdgesOf(jobInvocation)) {
                dependencies.add(edge.getTarget());
            }

            return dependencies;
        }

        public Set<JobInvocation> getIncomingDependencies(JobInvocation jobInvocation) {
            Set<JobInvocation> dependencies = new HashSet<JobInvocation>();
            for(FlowRun.JobEdge edge: graph.incomingEdgesOf(jobInvocation)) {
                dependencies.add(edge.getSource());
            }

            return dependencies;
        }

        public int getLongestPathToRoot(JobInvocation jobInvocation) {
            return getLongestPathToRoot(jobInvocation, 0);
        }

        // getVertexDepth
        private int getLongestPathToRoot(JobInvocation jobInvocation, int depth) {
            Set<JobInvocation> incomingDependencies = getIncomingDependencies(jobInvocation);
            int maxDepth = depth;
//            LOGGER.log(Level.SEVERE, ">>> job = " + jobInvocation + ", start depth = " + depth + ", edges = " + incomingDependencies.size());
            for (JobInvocation dependency: incomingDependencies) {
                int depDepth = getLongestPathToRoot(dependency, depth + 1);
                if (depDepth > maxDepth) {
                    maxDepth = depDepth;
                }
            }
//            LOGGER.log(Level.SEVERE, ">>> job = " + jobInvocation + ", max depth = " + depth);
            return maxDepth;
        }
    }

    /**
     * {@link BuildGrid} implementation that lays things out via its upstream/downstream relationship.
     */
    public static final class BuildGridImpl extends DefaultBuildGridImpl {
        private FlowGraph flowGraph;
        private Set<JobInvocation> placedBuilds = new HashSet<JobInvocation>();

        public BuildGridImpl(FlowGraph flowGraph) {
            this.flowGraph = flowGraph;
        }

        private BuildGridImpl(final FlowRun flowBuild) {
            flowGraph = new FlowGraph(flowBuild.getStartJob(), flowBuild.getJobsGraph());

            placeBuildInGrid(0, 0, flowBuild.getStartJob());
        }

        /**
         * Function called recursively to place a build form in a grid
         *
         * @param row    build will be placed in the starting row and 1st child as well. Each subsequent child
         *                       will be placed in a row below the previous.
         * @param column build will be placed in starting column. All children will be placed in next column.
         * @param build      build to be placed
         */
        private int placeBuildInGrid(final int row, final int column, final JobInvocation build) {

            int longestPathToRoot = flowGraph.getLongestPathToRoot(build);

//            LOGGER.log(Level.SEVERE, ">>> row = " + row + ", column = " + column + ", build = " + build
//                    + ", flow build = " + build.getFlowRun() + ",depth = " + longestPathToRoot + ", placed = " + placedBuilds.contains(build));


            if (longestPathToRoot == column && !placedBuilds.contains(build)) {
                placedBuilds.add(build);

                try {
                    set(row, column, new BuildForm(new PipelineBuild((AbstractBuild)build.getBuild())));
                } catch (Exception e) {
                    // todo
                    e.printStackTrace();
                }

                Set<JobInvocation> outgoingDependencies = flowGraph.getOutgoingDependencies(build);
                int nextRow = row;
                if (outgoingDependencies.size() > 0) {
                    for (JobInvocation dependency: outgoingDependencies) {
                        nextRow = placeBuildInGrid(nextRow, column + 1, dependency);
                    }

                    return nextRow;
                } else {
                    return nextRow + 1;
                }
            }

            return row;
        }

//        @Override
//        public BuildForm get(int row, int col) {
//            BuildForm buildForm = super.get(row, col);
//            LOGGER.log(Level.SEVERE, "getting row = " + row + ", column = " + col + ", found = " + buildForm);
//            return buildForm;
//        }
    }

    public String getFirstJob() {
        return firstJob;
    }

    /**
     * The job that's configured as the head of the pipeline.
     *
     * @param owner View that this builder is operating under.
     * @return possibly null
     */
    public AbstractProject<?, ?> getFirstJob(BuildPipelineView owner) {
        return Jenkins.getInstance().getItem(firstJob, owner.getOwnerItemGroup(), AbstractProject.class);
    }

    @Override
    public boolean hasBuildPermission(BuildPipelineView owner) {
        final AbstractProject<?, ?> job = getFirstJob(owner);
        return job != null && job.hasPermission(Item.BUILD);
    }

    @Override
//    @RequirePOST
    public HttpResponse doBuild(StaplerRequest req, @AncestorInPath BuildPipelineView owner) throws IOException {
        final AbstractProject<?, ?> p = getFirstJob(owner);
        if (p == null) {
//            return HttpResponses.error(StaplerResponse.SC_BAD_REQUEST, "No such project: " + getFirstJob());
            return HttpResponses.error(StaplerResponse.SC_BAD_REQUEST, new NullPointerException("no project"));
        }

        return new HttpResponse() {
//            @Override
            public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                rsp.sendRedirect("..");
                rsp.setStatus(200);
//                p.doBuild(req, rsp, new TimeDuration(0));

            }
        };
    }

    @Override
    public ProjectGrid build(BuildPipelineView owner) {
        return new GridImpl(getFirstJob(owner));
    }

    @Override
    public void onJobRenamed(BuildPipelineView owner, Item item, String oldName, String newName) throws IOException {
        if (item instanceof AbstractProject) {
            if ((oldName != null) && (oldName.equals(this.firstJob))) {
                this.firstJob = newName;
                owner.save();
            }
        }
    }

    /**
     * Descriptor.
     */
    @Extension(ordinal = 1000) // historical default behavior, so give it a higher priority
    public static class DescriptorImpl extends ProjectGridBuilderDescriptor {
        @Override
        public String getDisplayName() {
            return "Based on build flow relationship";
        }

        /**
         * Display Job List Item in the Edit View Page
         *
         * @param context What to resolve relative job names against?
         * @return ListBoxModel
         */
        public ListBoxModel doFillFirstJobItems(@AncestorInPath ItemGroup<?> context) {
            final hudson.util.ListBoxModel options = new hudson.util.ListBoxModel();
            for (final AbstractProject<?, ?> p : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
                if (p instanceof BuildFlow) {
                    options.add(p.getFullDisplayName(), p.getRelativeNameFrom(context));
                }
            }
            return options;
        }
    }
}
