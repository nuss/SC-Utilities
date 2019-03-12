SNRecorder {
	classvar <window, <recorderNChans=2, <>channelOffset=0, <>recorderBufSize, <>defaultName = "";
	classvar <>fileType, <>headerFormat;
	classvar <>storageLoc = "~/Music/SuperCollider_recordings/";
	classvar <>recServer, <recBuffer;
	classvar timeRecRoutine;
	classvar stopWatch;
	classvar recSynth;

	*initClass {
		this.recorderBufSize_(262144);
		this.setSynthDef(Server.default);
	}

	*setSynthDef { |server|
		SynthDescLib.all[\snSynthDefs] ?? {
			// store the synth in a separate SynthDescLib in order to avoid name clashes
			SynthDescLib(\snSynthDefs, [server]);
		};
		SynthDef(\snRecorder, { |in, bufnum|
			DiskOut.ar(bufnum, In.ar(in, this.recorderNChans));
		}).add(\snSynthDefs);
	}

	*recorderNChans_ { |numChannels|
		recorderNChans = numChannels.asInteger;
		this.setSynthDef;
	}

	*front {
		var myServerText, myServer, nChansText, nChans, chansOffsetText, chansOffset;
		var fileTypeText, fileType, headerFormatText, headerFormat;
		var bufSizeText, bufSize;
		var recordNameText, recordName;
		var pathText, path;
		var startStop;
		var myServers = Server.all.asArray;
		var myServerNames = myServers.collect(_.name);
		var fileTypes = [
			"WAV, signed 16 bit PCM",
			"WAV, signed 24 bit PCM",
			"WAV, signed 32 bit PCM",
			"WAV, 32 bit float",
			"AIFF, signed 16 bit PCM",
			"AIFF, signed 24 bit PCM",
			"AIFF, signed 32 bit PCM",
			"AIFF, 32 bit float",
			"Apple CAF, signed 16 bit PCM",
			"Apple CAF, signed 24 bit PCM",
			"Apple CAF, signed 32 bit PCM",
			"Apple CAF, 32 bit float",
			"SoundForge W64, signed 16 bit PCM",
			"SoundForge W64, signed 24 bit PCM",
			"SoundForge W64, signed 32 bit PCM",
			"SoundForge W64, 32 bit float",
			"FLAC, signed 16 bit PCM",
			"FLAC, signed 24 bit PCM"
		];
		var validHeaders = (
			wav: ["int16", "int24", "int32", "float"],
			aiff: ["int16", "int24", "int32", "float"],
			caf: ["int16", "int24", "int32", "float"],
			w64: ["int16", "int24", "int32", "float"],
			flac: ["int16", "int24"]
		);
		var index;

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

			myServerText = StaticText(window).string_("Server");
			myServer = PopUpMenu(window)
			.items_(myServerNames)
			.value_(myServers.indexOf(this.recServer) ?? {
				myServers.indexOf(Server.default)
			})
			.action_({ |p|
				this.recServer_(p.item);
			})
			;

			fileTypeText = StaticText(window).string_("file type");
			fileType = PopUpMenu(window)
			.items_(fileTypes)
			;

			if (this.fileType.isNil) { fileType.value_(3) };
			if (this.fileType.notNil) {
				if (this.headerFormat.isNil) {
					switch (this.fileType.asString.toLower,
						"wav", { fileType.value_(3) },
						"aiff", { fileType.value_(7) },
						"caf", { fileType.value_(11) },
						"w64", { fileType.value_(15) },
						"flac", { fileType.value_(17) },
						{ fileType.value_(3) }
					)
				} {
					switch (this.fileType.asString.toLower,
						"wav", {
							index = validHeaders.wav.indexOf(this.headerFormat);
							if (index.notNil) { fileType.value_(index) } { fileType.value_(3) };
						},
						"aiff", {
							index = validHeaders.aiff.indexOf(this.headerFormat);
							if (index.notNil) { fileType.value_(index + 4) } { fileType.value_(7) };
						},
						"caf", {
							index = validHeaders.caf.indexOf(this.headerFormat);
							if (index.notNil) { fileType.value_(index + 8) } { fileType.value_(11) };
						},
						"w64", {
							index = validHeaders.w64.indexOf(this.headerFormat);
							if (index.notNil) { fileType.value_(index + 12) } { fileType.value_(15) };
						},
						"flac", {
							index = validHeaders.flac.indexOf(this.headerFormat);
							if (index.notNil) { fileType.value_(index + 16) } { fileType.value_(17) };
						},
						{ fileType.value_(3) }
					)
				}
			};

			fileType.action_({ |p|
				switch (p.value,
					0, { this.fileType_("wav"); this.headerFormat_("int16") },
					1, { this.fileType_("wav"); this.headerFormat_("int24") },
					2, { this.fileType_("wav"); this.headerFormat_("int32") },
					3, { this.fileType_("wav"); this.headerFormat_("float") },
					4, { this.fileType_("aiff"); this.headerFormat_("int16") },
					5, { this.fileType_("aiff"); this.headerFormat_("int24") },
					6, { this.fileType_("aiff"); this.headerFormat_("int32") },
					7, { this.fileType_("aiff"); this.headerFormat_("flaot") },
					8, { this.fileType_("caf"); this.headerFormat_("int16") },
					9, { this.fileType_("caf"); this.headerFormat_("int24") },
					10, { this.fileType_("caf"); this.headerFormat_("int32") },
					11, { this.fileType_("caf"); this.headerFormat_("float") },
					12, { this.fileType_("w64"); this.headerFormat_("int16") },
					13, { this.fileType_("w64"); this.headerFormat_("int24") },
					14, { this.fileType_("w64"); this.headerFormat_("int32") },
					15, { this.fileType_("w64"); this.headerFormat_("float") },
					16, { this.fileType_("flac"); this.headerFormat_("int16") },
					17, { this.fileType_("flac"); this.headerFormat_("int24") }
				)
			});


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
			/*bufSizeText = StaticText(window).string_("power of 2 buffer size:");
			bufSize = NumberBox(window)
			.value_(recorderBufSize ? 262144)
			.clipLo_(512)
			.step_(1)
			.action_({ |n|
				this.recorderBufSize_(n.value);
			})
			;*/
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
							path.string ? storageLoc
						)
					},
					0, { this.stop }
				);
			})
			;

			window.layout_(VLayout(
				HLayout(stopWatch),
				HLayout(
					VLayout(myServerText, myServer),
					VLayout(fileTypeText, fileType),
					VLayout(chansOffsetText, chansOffset),
					VLayout(nChansText, nChans),
					/*bufSizeText, bufSize, */
					VLayout(recordNameText, recordName)
				),
				HLayout(pathText, path, startStop)
			));
		} {
			window.front;
		};

		SynthDescLib.global[\snRecorder] ?? {
			this.setSynthDef(myServers[myServer.value]);
		};
	}

	*record { |server, name, channelOffset, numChannels, recordingPath|
		var date;

		this.fileType ?? { this.fileType = "wav" };
		this.headerFormat ?? { this.headerFormat = "float" };
		numChannels !? { this.recorderNChans_(numChannels) };

		server.waitForBoot {
			server.bind {
				recBuffer = Buffer.alloc(server, this.recorderBufSize, this.recorderNChans);
				server.sync;
				date = Date.getDate;
				recBuffer.write(((recordingPath ? storageLoc) ++ name ++ "_" ++ date.stamp ++ "." ++ this.fileType).standardizePath, this.fileType, this.headerFormat, leaveOpen: true);
				recSynth = Synth.tail(nil, \snRecorder, [\in, channelOffset, \bufnum, recBuffer.bufnum]);
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