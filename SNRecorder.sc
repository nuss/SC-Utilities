SNRecorder {
	classvar <window, <recorderNChans=2, <>channelOffset=0, <>recorderBufSize, <>defaultName = "", myServer;
	classvar <>storageLoc = "~/Music/SuperCollider_recordings/";
	classvar <>recServer, <recBuffer;
	classvar timeRecRoutine;
	classvar stopWatch;
	classvar recSynth;

	*setSynthDef { |server|
		var synthDef;
		// FIXME: keep separate SynthDefs for each number of channels setting?
		// switching number of channels currently not possible
		SynthDescLib.at(\snSynthDefs) ?? {
			// store the synth in a separate SynthDescLib in order to avoid name clashes
			server !? { Server.all.add(server) };
			SynthDescLib(\snSynthDefs).servers_(Server.all);
		};
		synthDef = SynthDef(\snRecorder_ ++ this.recorderNChans, { |in, bufnum|
			DiskOut.ar(bufnum, In.ar(in, this.recorderNChans));
		});
		"does lib exist: %, server: %\n".postf(SynthDescLib.getLib(\snSynthDefs), SynthDescLib.getLib(\snSynthDefs).servers);
		// synthDef.inspect;
		synthDef.add(\snSynthDefs);
	}

	*recorderNChans_ { |numChannels|
		recorderNChans = numChannels.asInteger;
		this.setSynthDef(myServer.value ?? { Server.default });
	}

	*front {
		var /*myServer, */nChansText, nChans, chansOffsetText, chansOffset;
		var bufSizeText, bufSize;
		var recordNameText, recordName;
		var pathText, path;
		var startStop;
		var myServers = Server.all.asArray;
		var myServerNames = myServers.collect(_.name);

		if (window.isNil or: {
			window.isClosed
		}) {
			window = Window("recorder", Rect(0, 0, 600, 200), false).front;

			stopWatch = StaticText(window)
			.background_(Color(0.1, 0.1, 0.1))
			.stringColor_(Color.green)
			.string_("WAITING")
			.align_(\center)
			.font_(Font("Andale Mono", 100))
			;

			myServer = PopUpMenu(window)
			.items_(myServerNames)
			.value_(myServers.indexOf(this.recServer) ?? {
				myServers.indexOf(Server.default)
			})
			.action_({ |p|
				this.recServer_(p.item);
			})
			;

			chansOffsetText = StaticText(window).string_("ch. offset");
			chansOffset = NumberBox(window)
			.value_(this.channelOffset)
			.clipLo_(0)
			.step_(1)
			.action_({ |ch|
				this.channelOffset_(ch.value.asInteger);
			})
			;

			nChansText = StaticText(window).string_("n-ch:");
			nChans = NumberBox(window)
			.value_(this.recorderNChans)
			.clipLo_(1)
			.step_(1)
			.action_({ |n|
				this.recorderNChans_(n.value.asInteger);
			})
			;
			bufSizeText = StaticText(window).string_("power of 2 buffer size:");
			bufSize = NumberBox(window)
			.value_(recorderBufSize ? 262144)
			.clipLo_(512)
			.step_(1)
			.action_({ |n|
				this.recorderBufSize_(n.value);
			})
			;
			recordNameText = StaticText(window).string_("name:");
			recordName = TextField(window)
			.string_(this.defaultName)
			.action_({ |t|
				this.defaultName_(t.string);
			})
			;

			pathText = StaticText(window).string_("store file in:");
			path = TextField(window)
			.string_(this.storageLoc)
			.action_({ |t|
				this.storageLoc_(t.string);
			})
			;
			startStop = Button(window)
			.states_([
				["start", Color.black, Color.green],
				["stop", Color.white, Color.red]
			])
			.action_({ |b|
				switch (b.value,
					1, {
						this.record(
							myServers[myServer.value],
							recordName.string,
							channelOffset.value.asInteger,
							nChans.value.asInteger,
							bufSize.value,
							path.string ? storageLoc
						)
					},
					0, { this.stop }
				);
			})
			;

			window.layout_(VLayout(
				HLayout(stopWatch),
				HLayout(myServer, chansOffsetText, chansOffset, nChansText, nChans, bufSizeText, bufSize, recordNameText, recordName),
				HLayout(pathText, path, startStop)
			));
		} {
			window.front;
		};

		// think: myServers is an array now?
		SynthDescLib.global[\snRecorder] ?? {
			this.setSynthDef(myServers[myServer.value]);
		};
	}

	*record { |server, name, channelOffset, numChannels = 2, bufSize = 262144, recordingPath|
		var date;

		server.waitForBoot {
			server.bind {
				var time;

				recBuffer = Buffer.alloc(server, bufSize, numChannels);
				server.sync;
				date = Date.getDate;
				recBuffer.write(((recordingPath ? storageLoc) ++ name ++ "_" ++ date.stamp ++ ".wav").standardizePath, "wav", "float", leaveOpen: true);
				recSynth = Synth.tail(nil, ("snRecorder_" ++ recorderNChans).asSymbol, [\in, channelOffset, \bufnum, recBuffer.bufnum]);
				timeRecRoutine = fork ({
					inf.do{ |i|
						stopWatch.string_(i.asTimeString(1)[..7]).stringColor_(Color.red);
						1.wait;
					}
				}, AppClock);
			}
		}
	}

	*stop {
		recSynth.free;
		timeRecRoutine.reset.stop;
		stopWatch.string_("WAITING").stringColor_(Color.green).font_(Font("Andale Mono", 100));
		recBuffer.close({ |buf| buf.freeMsg });
	}

}