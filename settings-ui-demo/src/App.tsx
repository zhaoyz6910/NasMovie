/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { 
  MonitorSmartphone, 
  MoreVertical, 
  Lock, 
  Brush, 
  Info, 
  Film, 
  Heart, 
  Settings as SettingsIcon,
  ChevronRight
} from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';

type Tab = 'home' | 'favorites' | 'settings';

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>('settings');
  const [appLockEnabled, setAppLockEnabled] = useState(false);

  const renderContent = () => {
    switch (activeTab) {
      case 'home':
        return (
          <div className="flex flex-col items-center justify-center h-full text-zinc-500">
            <Film size={48} className="mb-4 opacity-20" />
            <p className="text-lg font-medium">首页内容</p>
          </div>
        );
      case 'favorites':
        return (
          <div className="flex flex-col items-center justify-center h-full text-zinc-500">
            <Heart size={48} className="mb-4 opacity-20" />
            <p className="text-lg font-medium">收藏内容</p>
          </div>
        );
      case 'settings':
        return (
          <div className="px-4 pt-12 pb-24">
            <h1 className="text-2xl font-bold text-center text-white mb-8">设置</h1>

            {/* Server Section */}
            <div className="mb-8">
              <h2 className="text-sm font-medium text-zinc-500 mb-3 px-1">服务器</h2>
              <div className="bg-[#1c1c1e] rounded-2xl overflow-hidden">
                <button className="w-full flex items-center justify-between p-4 active:bg-zinc-800 transition-colors">
                  <div className="flex items-center gap-4">
                    <div className="text-white">
                      <MonitorSmartphone size={24} />
                    </div>
                    <span className="text-lg text-white">服务器管理</span>
                  </div>
                  <MoreVertical size={20} className="text-blue-500" />
                </button>
              </div>
            </div>

            {/* Security Section */}
            <div className="mb-8">
              <h2 className="text-sm font-medium text-zinc-500 mb-3 px-1">安全</h2>
              <div className="flex flex-col gap-3">
                {/* App Lock */}
                <div className="bg-[#1c1c1e] rounded-2xl overflow-hidden flex items-center justify-between p-4">
                  <div className="flex items-center gap-4">
                    <div className="text-white">
                      <Lock size={24} />
                    </div>
                    <span className="text-lg text-white">应用锁</span>
                  </div>
                  <button 
                    onClick={() => setAppLockEnabled(!appLockEnabled)}
                    className={`w-12 h-7 rounded-full transition-colors relative ${appLockEnabled ? 'bg-blue-500' : 'bg-zinc-700'}`}
                  >
                    <motion.div 
                      animate={{ x: appLockEnabled ? 22 : 2 }}
                      className="absolute top-0.5 left-0.5 w-6 h-6 bg-white rounded-full shadow-sm"
                    />
                  </button>
                </div>

                {/* Clear Cache */}
                <div className="bg-[#1c1c1e] rounded-2xl overflow-hidden">
                  <button className="w-full flex items-center gap-4 p-4 active:bg-zinc-800 transition-colors">
                    <div className="text-white">
                      <Brush size={24} />
                    </div>
                    <span className="text-lg text-white">清除缓存</span>
                  </button>
                </div>

                {/* About */}
                <div className="bg-[#1c1c1e] rounded-2xl overflow-hidden">
                  <button className="w-full flex items-center gap-4 p-4 active:bg-zinc-800 transition-colors">
                    <div className="text-white">
                      <Info size={24} />
                    </div>
                    <span className="text-lg text-white">关于</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        );
    }
  };

  return (
    <div className="min-h-screen bg-black text-white font-sans selection:bg-blue-500/30">
      <main className="max-w-md mx-auto h-screen overflow-y-auto scrollbar-hide">
        <AnimatePresence mode="wait">
          <motion.div
            key={activeTab}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            transition={{ duration: 0.2 }}
            className="h-full"
          >
            {renderContent()}
          </motion.div>
        </AnimatePresence>
      </main>

      {/* Bottom Navigation */}
      <nav className="fixed bottom-0 left-0 right-0 bg-black/80 backdrop-blur-xl border-t border-zinc-800/50 pb-safe">
        <div className="max-w-md mx-auto flex justify-around items-center h-16">
          <NavButton 
            active={activeTab === 'home'} 
            onClick={() => setActiveTab('home')}
            icon={<Film size={24} />}
            label="首页"
          />
          <NavButton 
            active={activeTab === 'favorites'} 
            onClick={() => setActiveTab('favorites')}
            icon={<Heart size={24} />}
            label="收藏"
          />
          <NavButton 
            active={activeTab === 'settings'} 
            onClick={() => setActiveTab('settings')}
            icon={<SettingsIcon size={24} />}
            label="设置"
          />
        </div>
      </nav>
    </div>
  );
}

function NavButton({ active, onClick, icon, label }: { active: boolean, onClick: () => void, icon: React.ReactNode, label: string }) {
  return (
    <button 
      onClick={onClick}
      className={`flex flex-col items-center justify-center gap-1 transition-colors ${active ? 'text-blue-500' : 'text-zinc-500'}`}
    >
      <div className="relative">
        {icon}
        {active && (
          <motion.div 
            layoutId="nav-glow"
            className="absolute inset-0 bg-blue-500/20 blur-lg rounded-full -z-10"
          />
        )}
      </div>
      <span className="text-[10px] font-medium">{label}</span>
    </button>
  );
}
