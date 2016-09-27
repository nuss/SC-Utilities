MyRecorder {
	classvar <window, <>recorderNChans, <>recorderBufSize, <>defaultName = "";
	classvar <>storageLoc = "~/Music/SuperCollider_recordings/";
	classvar <>recServer, <recBuffer;
	classvar timeRecRoutine;
	classvar stopWatch;
	classvar recSynth;

	*setSynthDef { |numChannels = 2|
		this.recorderNChans_(numChannels);
		SynthDef(\myRecorder, { |bufnum|
			DiskOut.ar(bufnum, In.ar(0, numChannels));
		}).add;
	}

	*front {
		var myServer, nChansText, nChans;
		var bufSizeText, bufSize;
		var recordNameText, recordName;
		var pathText, path;
		var startStop;
		var myServers = Server.all.asArray;
		var myServerNames = myServers.collect(_.name);

		SynthDescLib.global[\myRecorder] ?? {
			this.setSynthDef;
		};

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

			nChansText = StaticText(window)
				.string_("n-ch:")
			;
			nChans = NumberBox(window)
				.value_(recorderNChans ? 2)
				.clipLo_(1)
				.step_(1)
				.action_({ |n|
					this.recorderNChans_(n.value.asInteger);
				})
			;
			bufSizeText = StaticText(window)
				.string_("power of 2 buffer size:")
			;
			bufSize = NumberBox(window)
				.value_(recorderBufSize ? 262144)
				.clipLo_(512)
				.step_(1)
				.action_({ |n|
					this.recorderBufSize_(n.value);
				})
			;
			recordNameText = StaticText(window)
				.string_("name:")
			;
			recordName = TextField(window)
				.string_(this.defaultName)
				.action_({ |t|
					this.defaultName_(t.string);
				})
			;

			pathText = StaticText(window)
				.string_("store file in:")
			;
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
				HLayout(myServer, nChansText, nChans, bufSizeText, bufSize, recordNameText, recordName),
				HLayout(pathText, path, startStop)
			));
		} {
			window.front;
		}
	}

	*record { |server, name, numChannels = 2, bufSize = 262144, recordingPath|
		var date;

		server.bind {
			var time;
			recBuffer = Buffer.alloc(server, bufSize, numChannels);
			server.sync;
			date = Date.getDate;
			recBuffer.write(((recordingPath ? storageLoc) ++ name ++ "_" ++ date.stamp ++ ".wav").standardizePath, "wav", "float", leaveOpen: true);
			recSynth = Synth.tail(nil, \myRecorder, [\bufnum, this.recBuffer.bufnum]);
			timeRecRoutine = fork ({
				inf.do{ |i|
					stopWatch.string_(i.asTimeString(1)[..7]).stringColor_(Color.red);
					1.wait;
				}
			}, AppClock);
		}
	}

	*stop {
		recSynth.free;
		timeRecRoutine.reset.stop;
		stopWatch.string_("WAITING").stringColor_(Color.green).font_(Font("Andale Mono", 100));
		recBuffer.close({ |buf| buf.freeMsg });
	}

}