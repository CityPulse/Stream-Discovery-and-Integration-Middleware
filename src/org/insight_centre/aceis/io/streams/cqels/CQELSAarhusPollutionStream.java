package org.insight_centre.aceis.io.streams.cqels;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.deri.cqels.engine.ExecContext;
import org.insight_centre.aceis.eventmodel.EventDeclaration;
import org.insight_centre.aceis.io.rdf.RDFFileManager;
import org.insight_centre.aceis.observations.PollutionObservation;
import org.insight_centre.aceis.observations.SensorObservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.csvreader.CsvReader;
import com.google.gson.Gson;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.vocabulary.RDF;

public class CQELSAarhusPollutionStream extends CQELSSensorStream implements
		Runnable {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	CsvReader streamData;
	EventDeclaration ed;
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
	private Date startDate = null;
	private Date endDate = null;

	public static void main(String[] args) {
		try {
			List<String> payloads = new ArrayList<String>();
			payloads.add(RDFFileManager.defaultPrefix + "Property-1|"
					+ RDFFileManager.defaultPrefix + "FoI-1|"
					+ RDFFileManager.ctPrefix + "API");
			EventDeclaration ed = new EventDeclaration("testEd", "testsrc",
					"air_pollution", null, payloads, 5.0);
			CQELSAarhusPollutionStream aps = new CQELSAarhusPollutionStream(null,
					"testuri", "streams/pollutionData158324.stream", ed);
			Thread th = new Thread(aps);
			th.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public CQELSAarhusPollutionStream(ExecContext context, String uri,
			String txtFile, EventDeclaration ed) throws IOException {
		super(context, uri);
		streamData = new CsvReader(String.valueOf(txtFile));
		this.ed = ed;
		streamData.setTrimWhitespace(false);
		streamData.setDelimiter(',');
		streamData.readHeaders();
	}

	public CQELSAarhusPollutionStream(ExecContext context, String uri,
			String txtFile, EventDeclaration ed, Date start, Date end)
			throws IOException {
		super(context, uri);

		streamData = new CsvReader(String.valueOf(txtFile));
		this.ed = ed;
		streamData.setTrimWhitespace(false);
		streamData.setDelimiter(',');
		streamData.readHeaders();
		this.startDate = start;
		this.endDate = end;
	}

	@Override
	public void run() {
		logger.info("Starting sensor stream: " + this.getURI());
		try {
			while (streamData.readRecord() && !stop) {
				Date obTime = sdf.parse(streamData.get("timestamp"));
				if (this.startDate != null && this.endDate != null) {
					if (obTime.before(this.startDate)
							|| obTime.after(this.endDate)) {
						logger.debug(this.getURI()
								+ ": Disgarded observation @" + obTime);
						continue;
					}
				}
				// logger.info("Reading data: " + streamData.toString());
				PollutionObservation po = (PollutionObservation) this
						.createObservation(streamData);
				logger.debug("Reading data: " + new Gson().toJson(po));
				List<Statement> stmts = this.getStatements(po);
				for (Statement st : stmts) {
					try {
						logger.debug(this.getURI() + " Streaming: "
								+ st.toString());
						stream(st.getSubject().asNode(), st.getPredicate()
								.asNode(), st.getObject().asNode());
						logger.info("Messages streamed to CQELS successfully.");

					} catch (Exception e) {
						e.printStackTrace();
						logger.error(this.getURI() + " CQELS streamming error.");
					}
					// messageByte += st.toString().getBytes().length;
				}
				// logger.info("Messages streamed to CQELS successfully.");
				try {
					Thread.sleep(sleep);
				} catch (Exception e) {

					e.printStackTrace();
					this.stop();
				}

			}
		} catch (Exception e) {
			logger.error("Unexpected thread termination");

		} finally {
			logger.info("Stream Terminated: " + this.getURI());
			this.stop();
		}

	}

	@Override
	protected List<Statement> getStatements(SensorObservation so) {
		Model m = ModelFactory.createDefaultModel();
		if (ed != null)
			for (String s : ed.getPayloads()) {
				this.annotateObservation(m, s, (PollutionObservation) so);
			}
		return m.listStatements().toList();
	}

	private void annotateObservation(Model m, String s, PollutionObservation so) {
		Resource observation = m.createResource("Observation-"
				+ UUID.randomUUID());
		// System.out.println("OB: " + observation.toString());
		observation.addProperty(RDF.type,
				m.createResource(RDFFileManager.ssnPrefix + "Observation"));
		// observation.addProperty(RDF.type,
		// m.createResource(RDFFileManager.saoPrefix + "StreamData"));
		Resource serviceID = m.createResource(this.getURI());
		observation.addProperty(
				m.createProperty(RDFFileManager.ssnPrefix + "observedBy"),
				serviceID);
		// Resource property = m.createResource(s.split("\\|")[2]);
		// property.addProperty(RDF.type, m.createResource(s.split("\\|")[0]));
		observation
				.addProperty(
						m.createProperty(RDFFileManager.ssnPrefix
								+ "observedProperty"),
						m.createResource(s.split("\\|")[0]));
		Property hasValue = m.createProperty(RDFFileManager.saoPrefix
				+ "hasValue");
		// Literal l;
		// System.out.println("Annotating: " + observedProperty.toString());
		// if (observedProperty.contains("AvgSpeed"))
		observation.addLiteral(hasValue, so.getApi());

	}

	@Override
	protected SensorObservation createObservation(Object data) {
		try {
			CsvReader streamData = (CsvReader) data;
			int ozone = Integer.parseInt(streamData.get("ozone")), particullate_matter = Integer
					.parseInt(streamData.get("particullate_matter")), carbon_monoxide = Integer
					.parseInt(streamData.get("carbon_monoxide")), sulfure_dioxide = Integer
					.parseInt(streamData.get("sulfure_dioxide")), nitrogen_dioxide = Integer
					.parseInt(streamData.get("nitrogen_dioxide"));
			Date obTime = sdf.parse(streamData.get("timestamp"));
			PollutionObservation po = new PollutionObservation(0.0, 0.0, 0.0,
					ozone, particullate_matter, carbon_monoxide,
					sulfure_dioxide, nitrogen_dioxide, obTime);
			logger.debug(this.getURI() + ": streaming record @"
					+ po.getObTimeStamp());
			this.currentObservation = po;
			return po;
		} catch (NumberFormatException | IOException | ParseException e) {
			e.printStackTrace();
		}
		return null;
	}
}
