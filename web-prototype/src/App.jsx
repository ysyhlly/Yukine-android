import { useMemo, useState } from 'react';
import {
  BatteryFull,
  Books,
  CaretRight,
  CellSignalFull,
  Check,
  ClockCounterClockwise,
  CloudArrowDown,
  Disc,
  DownloadSimple,
  FolderSimple,
  GearSix,
  GridFour,
  Heart,
  House,
  ListBullets,
  MagnifyingGlass,
  MusicNotesSimple,
  Pause,
  Play,
  Playlist,
  Queue,
  Shuffle,
  SkipBack,
  SlidersHorizontal,
  Sparkle,
  UserSound,
  Waveform,
  WifiHigh,
  X,
} from '@phosphor-icons/react';
import './styles.css';

const A = '/assets/';

const recent = [
  { title: 'Cage', artist: 'Luna', cover: 'cover-walkway.png' },
  { title: 'NPC', artist: 'picco', cover: 'cover-npc.png' },
  { title: 'TOMATO', artist: 'MIMI', cover: 'cover-tomato.png' },
  { title: '1001夜', artist: 'しほ', cover: 'cover-night.png' },
];

const libraryTiles = [
  { label: '专辑', count: 347, icon: Disc },
  { label: '艺人', count: 237, icon: UserSound },
  { label: '歌单', count: 12, icon: Playlist },
  { label: '文件夹', count: 8, icon: FolderSimple },
];

const personalRows = [
  { label: '喜欢的音乐', count: '24 首', icon: Heart, tone: 'pink' },
  { label: '最近添加', count: '18 首', icon: ClockCounterClockwise, tone: 'blue' },
  { label: '已下载', count: '6 首', icon: DownloadSimple, tone: 'mint' },
];

function StatusBar() {
  return (
    <div className="status-bar" aria-label="状态栏">
      <span>9:41</span>
      <div className="status-icons">
        <CellSignalFull weight="fill" />
        <WifiHigh weight="bold" />
        <BatteryFull weight="fill" />
      </div>
    </div>
  );
}

function Search({ value, onChange, onSubmit, placeholder = '搜索歌曲、专辑或艺人' }) {
  return (
    <form className="search" onSubmit={onSubmit}>
      <MagnifyingGlass size={19} weight="bold" />
      <input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
      />
      {value && (
        <button type="button" aria-label="清空搜索" onClick={() => onChange('')}>
          <X size={16} weight="bold" />
        </button>
      )}
    </form>
  );
}

function CoverStack({ compact = false }) {
  return (
    <div className={`cover-stack ${compact ? 'compact' : ''}`} aria-hidden="true">
      <img src={`${A}cover-canyon.png`} alt="" />
      <img src={`${A}cover-ice.png`} alt="" />
      <img src={`${A}cover-aurora.png`} alt="" />
    </div>
  );
}

function HomeScreen({ isPlaying, setIsPlaying, query, setQuery, notify, openSheet }) {
  return (
    <div className="screen home-screen">
      <header className="home-header">
        <div>
          <h1>早上好</h1>
          <p>今天想听点什么？</p>
        </div>
        <div className="brand-orb"><img src={`${A}cover-aurora.png`} alt="YUKINE" /></div>
      </header>

      <Search
        value={query}
        onChange={setQuery}
        placeholder="搜索本地和在线音乐"
        onSubmit={(event) => { event.preventDefault(); notify(query ? `正在搜索“${query}”` : '输入关键词开始搜索'); }}
      />

      <button className="continue-card" onClick={() => setIsPlaying(!isPlaying)}>
        <CoverStack />
        <div className="continue-copy">
          <span>接着听</span>
          <strong>今天的播放队列</strong>
          <small>从上次位置继续 · 18 首</small>
          <i><b /></i>
        </div>
        <span className="round-play">
          {isPlaying ? <Pause weight="fill" /> : <Play weight="fill" />}
        </span>
      </button>

      <div className="quick-actions">
        <button onClick={() => openSheet('queue')}><Queue size={21} weight="bold" />查看队列</button>
        <button onClick={() => notify('已随机播放你的曲库')}><Shuffle size={21} weight="bold" />随机播放</button>
      </div>

      <section className="section recent-section">
        <div className="section-title"><h2>最近播放</h2><button onClick={() => notify('已展开全部最近播放')}>查看全部</button></div>
        <div className="recent-grid">
          {recent.map((item) => (
            <button className="album-card" key={item.title} onClick={() => notify(`准备播放 ${item.title}`)}>
              <img src={`${A}${item.cover}`} alt={`${item.title} 专辑封面`} />
              <strong>{item.title}</strong>
              <span>{item.artist}</span>
            </button>
          ))}
        </div>
      </section>

      <section className="section discovery-section">
        <div className="section-title"><h2>今天听什么</h2></div>
        <div className="recommend-list">
          <button onClick={() => notify('每日推荐已更新')}>
            <span className="recommend-icon daily"><Sparkle weight="fill" /></span>
            <span><strong>每日推荐</strong><small>为你挑选的 30 首歌</small></span>
            <CaretRight />
          </button>
          <button onClick={() => notify('已进入心动推荐')}>
            <span className="recommend-icon heartbeat"><Heart weight="fill" /></span>
            <span><strong>心动推荐</strong><small>从喜欢出发，发现新声音</small></span>
            <CaretRight />
          </button>
        </div>
      </section>
    </div>
  );
}

function LibraryScreen({ query, setQuery, notify, openSheet }) {
  return (
    <div className="screen library-screen">
      <header className="library-header">
        <div><h1>曲库</h1></div>
        <button aria-label="管理曲库" onClick={() => notify('曲库管理模式已开启')}><SlidersHorizontal size={22} weight="bold" /></button>
      </header>

      <Search
        value={query}
        onChange={setQuery}
        placeholder="搜索歌曲、专辑、艺人或歌单"
        onSubmit={(event) => { event.preventDefault(); notify(query ? `在曲库中搜索“${query}”` : '输入关键词开始搜索'); }}
      />

      <section className="section library-browse">
        <div className="section-title"><h2>浏览曲库</h2></div>
        <div className="library-card">
          <button className="all-songs" onClick={() => openSheet('songs')}>
            <CoverStack compact />
            <span><strong>全部歌曲</strong><small>392 首</small></span>
            <span className="all-play"><Play weight="fill" /></span>
          </button>
          <div className="tile-grid">
            {libraryTiles.map(({ label, count, icon: Icon }) => (
              <button key={label} onClick={() => notify(`已打开${label}`)}>
                <span className="tile-icon"><Icon size={20} weight="fill" /></span>
                <span><strong>{label}</strong><small>{count}</small></span>
                <CaretRight size={15} />
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className="section personal-section">
        <div className="section-title"><h2>我的音乐</h2></div>
        <div className="personal-card">
          {personalRows.map(({ label, count, icon: Icon, tone }) => (
            <button key={label} onClick={() => notify(`已打开${label}`)}>
              <span className={`personal-icon ${tone}`}><Icon size={20} weight="fill" /></span>
              <span><strong>{label}</strong><small>{count}</small></span>
              <CaretRight size={16} />
            </button>
          ))}
        </div>
      </section>

      <section className="section source-section">
        <div className="section-title"><h2>音乐来源</h2></div>
        <button className="source-card" onClick={() => openSheet('sources')}>
          <span className="source-icon"><CloudArrowDown size={22} weight="fill" /></span>
          <span><strong>本机 · WebDAV · 在线导入</strong><small>3 个来源</small></span>
          <CaretRight size={17} />
        </button>
      </section>
    </div>
  );
}

function Placeholder({ type }) {
  const isSettings = type === 'settings';
  const Icon = isSettings ? GearSix : Waveform;
  return (
    <div className="screen placeholder-screen">
      <div className="placeholder-orb"><Icon size={38} weight="duotone" /></div>
      <h1>{isSettings ? '设置' : '正在播放'}</h1>
      <p>{isSettings ? '偏好设置将在正式版本中接入。' : '完整播放页将在下一版 Demo 中展开。'}</p>
    </div>
  );
}

function NowBar({ isPlaying, setIsPlaying, openSheet }) {
  return (
    <button className="now-bar" onClick={() => openSheet('now')}>
      <img src={`${A}cover-night.png`} alt="Heal Me 专辑封面" />
      <span><strong>Heal Me feat. しほ</strong><small>Luna / picco</small></span>
      <span className="now-previous" aria-hidden="true"><SkipBack weight="fill" /></span>
      <span
        role="button"
        tabIndex="0"
        className="now-play"
        aria-label={isPlaying ? '暂停' : '播放'}
        onClick={(event) => { event.stopPropagation(); setIsPlaying(!isPlaying); }}
        onKeyDown={(event) => { if (event.key === 'Enter') setIsPlaying(!isPlaying); }}
      >
        {isPlaying ? <Pause weight="fill" /> : <Play weight="fill" />}
      </span>
    </button>
  );
}

const tabs = [
  { id: 'home', label: '首页', icon: House },
  { id: 'library', label: '曲库', icon: Books },
  { id: 'playing', label: '播放', icon: Waveform },
  { id: 'settings', label: '设置', icon: GearSix },
];

function BottomNav({ active, setActive }) {
  return (
    <nav className="bottom-nav" aria-label="主导航">
      {tabs.map(({ id, label, icon: Icon }) => (
        <button className={active === id ? 'active' : ''} key={id} onClick={() => setActive(id)}>
          <Icon size={22} weight={active === id ? 'fill' : 'regular'} />
          <span>{label}</span>
        </button>
      ))}
    </nav>
  );
}

function Sheet({ type, close, notify }) {
  const content = useMemo(() => {
    if (type === 'queue') return { title: '播放队列', icon: ListBullets, rows: ['Heal Me feat. しほ', 'Cage', 'TOMATO'] };
    if (type === 'songs') return { title: '全部歌曲', icon: MusicNotesSimple, rows: ['Heal Me feat. しほ', 'Cage', '1001夜'] };
    if (type === 'sources') return { title: '音乐来源', icon: CloudArrowDown, rows: ['本机音乐', 'WebDAV', '在线导入'] };
    return { title: '正在播放', icon: Waveform, rows: ['Heal Me feat. しほ', 'Luna / picco', '专辑：Heal Me'] };
  }, [type]);
  const Icon = content.icon;
  return (
    <div className="sheet-backdrop" onClick={close}>
      <section className="sheet" onClick={(event) => event.stopPropagation()}>
        <div className="sheet-handle" />
        <div className="sheet-title"><span><Icon size={22} weight="fill" /></span><h2>{content.title}</h2><button onClick={close}><X /></button></div>
        <div className="sheet-rows">
          {content.rows.map((row, index) => (
            <button key={row} onClick={() => notify(index === 0 && type !== 'sources' ? `准备播放 ${row}` : `已选择${row}`)}>
              {type === 'songs' || type === 'queue' || type === 'now' ? <img src={`${A}${recent[index]?.cover || 'cover-aurora.png'}`} alt="" /> : <span className="source-dot"><Check weight="bold" /></span>}
              <span>{row}</span><CaretRight size={16} />
            </button>
          ))}
        </div>
      </section>
    </div>
  );
}

export function App() {
  const [active, setActive] = useState('home');
  const [isPlaying, setIsPlaying] = useState(false);
  const [query, setQuery] = useState('');
  const [toast, setToast] = useState('');
  const [sheet, setSheet] = useState(null);

  function notify(message) {
    setToast(message);
    window.clearTimeout(window.__yukineToast);
    window.__yukineToast = window.setTimeout(() => setToast(''), 1800);
  }

  return (
    <main className="mobile-prototype discrete-revision">
      <StatusBar />
      <div className="ambient ambient-one" />
      <div className="ambient ambient-two" />
      {active === 'home' && <HomeScreen {...{ isPlaying, setIsPlaying, query, setQuery, notify }} openSheet={setSheet} />}
      {active === 'library' && <LibraryScreen {...{ query, setQuery, notify }} openSheet={setSheet} />}
      {(active === 'playing' || active === 'settings') && <Placeholder type={active} />}
      <NowBar {...{ isPlaying, setIsPlaying }} openSheet={setSheet} />
      <BottomNav {...{ active, setActive }} />
      {toast && <div className="toast"><Check size={15} weight="bold" />{toast}</div>}
      {sheet && <Sheet type={sheet} close={() => setSheet(null)} notify={notify} />}
    </main>
  );
}
