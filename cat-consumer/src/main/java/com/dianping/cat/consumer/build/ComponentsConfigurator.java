package com.dianping.cat.consumer.build;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.unidal.initialization.Module;
import org.unidal.lookup.configuration.AbstractResourceConfigurator;
import org.unidal.lookup.configuration.Component;

import com.dianping.cat.analysis.MessageAnalyzer;
import com.dianping.cat.analysis.MessageAnalyzerManager;
import com.dianping.cat.config.content.ContentFetcher;
import com.dianping.cat.config.content.DefaultContentFetcher;
import com.dianping.cat.configuration.ServerConfigManager;
import com.dianping.cat.consumer.CatConsumerModule;
import com.dianping.cat.consumer.RealtimeConsumer;
import com.dianping.cat.consumer.dal.BusinessReportDao;
import com.dianping.cat.consumer.cross.CrossAnalyzer;
import com.dianping.cat.consumer.cross.CrossDelegate;
import com.dianping.cat.consumer.cross.IpConvertManager;
import com.dianping.cat.consumer.dependency.DatabaseParser;
import com.dianping.cat.consumer.dependency.DependencyAnalyzer;
import com.dianping.cat.consumer.dependency.DependencyDelegate;
import com.dianping.cat.consumer.dump.DumpAnalyzer;
import com.dianping.cat.consumer.dump.LocalMessageBucketManager;
import com.dianping.cat.consumer.event.EventAnalyzer;
import com.dianping.cat.consumer.event.EventDelegate;
import com.dianping.cat.consumer.heartbeat.HeartbeatAnalyzer;
import com.dianping.cat.consumer.heartbeat.HeartbeatDelegate;
import com.dianping.cat.consumer.matrix.MatrixAnalyzer;
import com.dianping.cat.consumer.matrix.MatrixDelegate;
import com.dianping.cat.consumer.metric.MetricAnalyzer;
import com.dianping.cat.consumer.metric.MetricConfigManager;
import com.dianping.cat.consumer.problem.DefaultProblemHandler;
import com.dianping.cat.consumer.problem.LongExecutionProblemHandler;
import com.dianping.cat.consumer.problem.ProblemAnalyzer;
import com.dianping.cat.consumer.problem.ProblemDelegate;
import com.dianping.cat.consumer.problem.ProblemHandler;
import com.dianping.cat.consumer.productline.ProductLineConfigManager;
import com.dianping.cat.consumer.state.StateAnalyzer;
import com.dianping.cat.consumer.state.StateDelegate;
import com.dianping.cat.consumer.top.TopAnalyzer;
import com.dianping.cat.consumer.top.TopDelegate;
import com.dianping.cat.consumer.transaction.TransactionAnalyzer;
import com.dianping.cat.consumer.transaction.TransactionDelegate;
import com.dianping.cat.core.config.ConfigDao;
import com.dianping.cat.core.dal.HourlyReportContentDao;
import com.dianping.cat.core.dal.HourlyReportDao;
import com.dianping.cat.core.dal.ProjectDao;
import com.dianping.cat.hadoop.hdfs.LogviewUploader;
import com.dianping.cat.message.spi.core.MessageConsumer;
import com.dianping.cat.message.spi.core.MessagePathBuilder;
import com.dianping.cat.service.DefaultReportManager;
import com.dianping.cat.service.HostinfoService;
import com.dianping.cat.service.ProjectService;
import com.dianping.cat.service.ReportDelegate;
import com.dianping.cat.service.ReportManager;
import com.dianping.cat.statistic.ServerStatisticManager;
import com.dianping.cat.storage.message.MessageBucketManager;
import com.dianping.cat.storage.report.ReportBucketManager;
import com.dianping.cat.task.TaskManager;

public class ComponentsConfigurator extends AbstractResourceConfigurator {
	public static void main(String[] args) {
		generatePlexusComponentsXmlFile(new ComponentsConfigurator());
	}

	@Override
	public List<Component> defineComponents() {
		List<Component> all = new ArrayList<Component>();

		all.add(C(MessageConsumer.class, RealtimeConsumer.class) //
		      .req(MessageAnalyzerManager.class, ServerStatisticManager.class));

		all.addAll(defineTransactionComponents());
		all.addAll(defineEventComponents());
		all.addAll(defineProblemComponents());
		all.addAll(defineHeartbeatComponents());
		all.addAll(defineTopComponents());
		all.addAll(defineDumpComponents());
		all.addAll(defineStateComponents());
		all.addAll(defineCrossComponents());
		all.addAll(defineMatrixComponents());
		all.addAll(defineDependencyComponents());
		all.addAll(defineMetricComponents());

		all.add(C(Module.class, CatConsumerModule.ID, CatConsumerModule.class));
		all.addAll(new CatDatabaseConfigurator().defineComponents());
		return all;
	}

	private Collection<Component> defineCrossComponents() {
		final List<Component> all = new ArrayList<Component>();
		final String ID = CrossAnalyzer.ID;

		all.add(C(IpConvertManager.class));
		all.add(C(MessageAnalyzer.class, ID, CrossAnalyzer.class).is(PER_LOOKUP) //
		      .req(ReportManager.class, ID).req(ServerConfigManager.class, IpConvertManager.class));
		all.add(C(ReportManager.class, ID, DefaultReportManager.class) //
		      .req(ReportDelegate.class, ID) //
		      .req(ReportBucketManager.class, HourlyReportDao.class, HourlyReportContentDao.class) //
		      .config(E("name").value(ID)));
		all.add(C(ReportDelegate.class, ID, CrossDelegate.class).req(TaskManager.class));

		return all;
	}

	private Collection<Component> defineDependencyComponents() {
		final List<Component> all = new ArrayList<Component>();
		final String ID = DependencyAnalyzer.ID;

		all.add(C(DatabaseParser.class));
		all.add(C(MessageAnalyzer.class, ID, DependencyAnalyzer.class).is(PER_LOOKUP).req(ReportManager.class, ID)
		      .req(ServerConfigManager.class, DatabaseParser.class));
		all.add(C(ReportManager.class, ID, DefaultReportManager.class) //
		      .req(ReportDelegate.class, ID) //
		      .req(ReportBucketManager.class, HourlyReportDao.class, HourlyReportContentDao.class) //
		      .config(E("name").value(ID)));
		all.add(C(ReportDelegate.class, ID, DependencyDelegate.class).req(TaskManager.class));

		return all;
	}

	private Collection<Component> defineDumpComponents() {
		final List<Component> all = new ArrayList<Component>();
		all.add(C(MessageAnalyzer.class, DumpAnalyzer.ID, DumpAnalyzer.class).is(PER_LOOKUP) //
		      .req(ServerStatisticManager.class, ServerConfigManager.class) //
		      .req(MessageBucketManager.class, LocalMessageBucketManager.ID));

		all.add(C(MessageBucketManager.class, LocalMessageBucketManager.ID, LocalMessageBucketManager.class) //
		      .req(ServerConfigManager.class, MessagePathBuilder.class, ServerStatisticManager.class)//
		      .req(LogviewUploader.class));

		return all;
	}

	private Collection<Component> defineEventComponents() {
		final List<Component> all = new ArrayList<Component>();
		final String ID = EventAnalyzer.ID;

		all.add(C(MessageAnalyzer.class, ID, EventAnalyzer.class).is(PER_LOOKUP) //
		      .req(ReportManager.class, ID).req(ServerConfigManager.class));
		all.add(C(ReportManager.class, ID, DefaultReportManager.class) //
		      .req(ReportDelegate.class, ID) //
		      .req(ReportBucketManager.class, HourlyReportDao.class, HourlyReportContentDao.class) //
		      .config(E("name").value(ID)));
		all.add(C(ReportDelegate.class, ID, EventDelegate.class).req(TaskManager.class, ServerConfigManager.class));

		return all;
	}

	private Collection<Component> defineHeartbeatComponents() {
		final List<Component> all = new ArrayList<Component>();
		final String ID = HeartbeatAnalyzer.ID;

		all.add(C(MessageAnalyzer.class, ID, HeartbeatAnalyzer.class).is(PER_LOOKUP) //
		      .req(ReportManager.class, ID).req(ServerConfigManager.class));
		all.add(C(ReportManager.class, ID, DefaultReportManager.class) //
		      .req(ReportDelegate.class, ID) //
		      .req(ReportBucketManager.class, HourlyReportDao.class, HourlyReportContentDao.class) //
		      .config(E("name").value(ID)));
		all.add(C(ReportDelegate.class, ID, HeartbeatDelegate.class).req(TaskManager.class));

		return all;
	}

	private Collection<Component> defineMatrixComponents() {
		final List<Component> all = new ArrayList<Component>();
		final String ID = MatrixAnalyzer.ID;

		all.add(C(MessageAnalyzer.class, ID, MatrixAnalyzer.class).is(PER_LOOKUP) //
		      .req(ReportManager.class, ID).req(ServerConfigManager.class));
		all.add(C(ReportManager.class, ID, DefaultReportManager.class) //
		      .req(ReportDelegate.class, ID) //
		      .req(ReportBucketManager.class, HourlyReportDao.class, HourlyReportContentDao.class) //
		      .config(E("name").value(ID)));
		all.add(C(ReportDelegate.class, ID, MatrixDelegate.class).req(TaskManager.class));

		return all;
	}

	private Collection<Component> defineMetricComponents() {
		final List<Component> all = new ArrayList<Component>();

		all.add(C(ContentFetcher.class, DefaultContentFetcher.class));
		all.add(C(ProductLineConfigManager.class).req(ConfigDao.class, ContentFetcher.class));
		all.add(C(MetricConfigManager.class).req(ConfigDao.class, ContentFetcher.class));
		all.add(C(MessageAnalyzer.class, MetricAnalyzer.ID, MetricAnalyzer.class).is(PER_LOOKUP) //
		      .req(ReportBucketManager.class, BusinessReportDao.class, MetricConfigManager.class)//
		      .req(ProductLineConfigManager.class, TaskManager.class, ServerConfigManager.class));

		return all;
	}

	private Collection<Component> defineProblemComponents() {
		final List<Component> all = new ArrayList<Component>();
		final String ID = ProblemAnalyzer.ID;

		all.add(C(ProblemHandler.class, DefaultProblemHandler.ID, DefaultProblemHandler.class)//
		      .config(E("failureType").value("URL,SQL,Call,PigeonCall,Cache"))//
		      .config(E("errorType").value("Error,RuntimeException,Exception")));

		all.add(C(ProblemHandler.class, LongExecutionProblemHandler.ID, LongExecutionProblemHandler.class) //
		      .req(ServerConfigManager.class));

		all.add(C(MessageAnalyzer.class, ID, ProblemAnalyzer.class).is(PER_LOOKUP) //
		      .req(ReportManager.class, ID).req(ServerConfigManager.class).req(ProblemHandler.class, //
		            new String[] { DefaultProblemHandler.ID, LongExecutionProblemHandler.ID }, "m_handlers"));
		all.add(C(ReportManager.class, ID, DefaultReportManager.class) //
		      .req(ReportDelegate.class, ID) //
		      .req(ReportBucketManager.class, HourlyReportDao.class, HourlyReportContentDao.class) //
		      .config(E("name").value(ID)));
		all.add(C(ReportDelegate.class, ID, ProblemDelegate.class) //
		      .req(TaskManager.class, ServerConfigManager.class));

		return all;
	}

	private Collection<Component> defineStateComponents() {
		final List<Component> all = new ArrayList<Component>();
		final String ID = StateAnalyzer.ID;

		all.add(C(ProjectService.class).req(ProjectDao.class, ServerConfigManager.class));
		all.add(C(MessageAnalyzer.class, ID, StateAnalyzer.class).is(PER_LOOKUP).req(ReportManager.class, ID)
		      .req(ServerConfigManager.class, HostinfoService.class, ProjectService.class, ServerStatisticManager.class));
		all.add(C(ReportManager.class, ID, DefaultReportManager.class) //
		      .req(ReportDelegate.class, ID) //
		      .req(ReportBucketManager.class, HourlyReportDao.class, HourlyReportContentDao.class) //
		      .config(E("name").value(ID)));
		all.add(C(ReportDelegate.class, ID, StateDelegate.class).req(TaskManager.class, ReportBucketManager.class));

		return all;
	}

	private Collection<Component> defineTopComponents() {
		final List<Component> all = new ArrayList<Component>();
		final String ID = TopAnalyzer.ID;

		all.add(C(MessageAnalyzer.class, ID, TopAnalyzer.class).is(PER_LOOKUP) //
		      .req(ReportManager.class, ID).req(ServerConfigManager.class));
		all.add(C(ReportManager.class, ID, DefaultReportManager.class) //
		      .req(ReportDelegate.class, ID) //
		      .req(ReportBucketManager.class, HourlyReportDao.class, HourlyReportContentDao.class) //
		      .config(E("name").value(ID)));
		all.add(C(ReportDelegate.class, ID, TopDelegate.class));

		return all;
	}

	private Collection<Component> defineTransactionComponents() {
		final List<Component> all = new ArrayList<Component>();
		final String ID = TransactionAnalyzer.ID;

		all.add(C(MessageAnalyzer.class, ID, TransactionAnalyzer.class).is(PER_LOOKUP) //
		      .req(ReportManager.class, ID).req(ReportDelegate.class, ID).req(ServerConfigManager.class));
		all.add(C(ReportManager.class, ID, DefaultReportManager.class) //
		      .req(ReportDelegate.class, ID) //
		      .req(ReportBucketManager.class, HourlyReportDao.class, HourlyReportContentDao.class) //
		      .config(E("name").value(ID)));
		all.add(C(ReportDelegate.class, ID, TransactionDelegate.class).req(TaskManager.class, ServerConfigManager.class));

		return all;
	}
}
