import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import Text from '../ui/Text';
import { TextVariants } from '../../types/typography';
import Slider from '../ui/Slider';
import Switch from '../ui/Switch';
import { SmartAlbum, SmartAlbumCriteria } from '../../hooks/useSmartAlbums';
import { COLOR_LABELS } from '../../utils/adjustments';

const COLOR_OPTIONS = COLOR_LABELS.map((c) => c.name);

const EMPTY_CRITERIA: SmartAlbumCriteria = {
    ai_tags: null,
    user_tags: null,
    min_rating: null,
    max_rating: null,
    color_labels: null,
    date_from: null,
    date_to: null,
    camera_models: null,
    lenses: null,
    raw_only: null,
    edited_only: null,
    description_search: null,
};

interface SmartAlbumModalProps {
    isOpen: boolean;
    onClose(): void;
    onSave(name: string, criteria: SmartAlbumCriteria): void;
    editingAlbum?: SmartAlbum | null;
}

export default function SmartAlbumModal({
    isOpen,
    onClose,
    onSave,
    editingAlbum,
}: SmartAlbumModalProps) {
    const { t } = useTranslation();
    const [name, setName] = useState('');
    const [criteria, setCriteria] = useState<SmartAlbumCriteria>({ ...EMPTY_CRITERIA });
    const [isMounted, setIsMounted] = useState(false);
    const [show, setShow] = useState(false);

    // Fields for comma-separated inputs
    const [aiTagsText, setAiTagsText] = useState('');
    const [userTagsText, setUserTagsText] = useState('');
    const [cameraModelsText, setCameraModelsText] = useState('');
    const [lensesText, setLensesText] = useState('');

    useEffect(() => {
        if (isOpen) {
            if (editingAlbum) {
                setName(editingAlbum.name);
                setCriteria({ ...editingAlbum.criteria });
                setAiTagsText((editingAlbum.criteria.ai_tags || []).join(', '));
                setUserTagsText((editingAlbum.criteria.user_tags || []).join(', '));
                setCameraModelsText((editingAlbum.criteria.camera_models || []).join(', '));
                setLensesText((editingAlbum.criteria.lenses || []).join(', '));
            } else {
                setName('');
                setCriteria({ ...EMPTY_CRITERIA });
                setAiTagsText('');
                setUserTagsText('');
                setCameraModelsText('');
                setLensesText('');
            }
            setIsMounted(true);
            const timer = setTimeout(() => setShow(true), 10);
            return () => clearTimeout(timer);
        } else {
            setShow(false);
            const timer = setTimeout(() => {
                setIsMounted(false);
            }, 300);
            return () => clearTimeout(timer);
        }
    }, [isOpen, editingAlbum]);

    const parseCommaList = (text: string): string[] | null => {
        const trimmed = text.trim();
        if (!trimmed) return null;
        return trimmed.split(',').map((s) => s.trim()).filter(Boolean);
    };

    const handleSave = useCallback(() => {
        if (!name.trim()) return;
        const finalCriteria: SmartAlbumCriteria = {
            ...criteria,
            ai_tags: parseCommaList(aiTagsText),
            user_tags: parseCommaList(userTagsText),
            camera_models: parseCommaList(cameraModelsText),
            lenses: parseCommaList(lensesText),
        };
        onSave(name.trim(), finalCriteria);
        onClose();
    }, [name, criteria, aiTagsText, userTagsText, cameraModelsText, lensesText, onSave, onClose]);

    const handleKeyDown = useCallback(
        (e: React.KeyboardEvent<HTMLDivElement>) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                e.stopPropagation();
                e.nativeEvent.stopImmediatePropagation();
                handleSave();
            } else if (e.key === 'Escape') {
                e.preventDefault();
                e.stopPropagation();
                e.nativeEvent.stopImmediatePropagation();
                onClose();
            }
        },
        [handleSave, onClose],
    );

    const toggleColorLabel = (color: string) => {
        const current = criteria.color_labels || [];
        const next = current.includes(color)
            ? current.filter((c) => c !== color)
            : [...current, color];
        setCriteria({ ...criteria, color_labels: next.length > 0 ? next : null });
    };

    if (!isMounted) {
        return null;
    }

    return (
        <div
            aria-modal="true"
            className={`
                fixed inset-0 flex items-center justify-center z-50
                bg-black/30 backdrop-blur-xs
                transition-opacity duration-300 ease-in-out
                ${show ? 'opacity-100' : 'opacity-0'}
            `}
            onClick={onClose}
            role="dialog"
        >
            <div
                className={`
                    bg-surface rounded-lg shadow-xl p-6 w-full max-w-lg max-h-[85vh] overflow-y-auto
                    transform transition-all duration-300 ease-out
                    ${show ? 'scale-100 opacity-100 translate-y-0' : 'scale-95 opacity-0 -translate-y-4'}
                `}
                onClick={(e) => e.stopPropagation()}
                onKeyDown={handleKeyDown}
            >
                <Text variant={TextVariants.title} className="mb-4">
                    {editingAlbum ? t('library.smartAlbums.edit') : t('library.smartAlbums.create')}
                </Text>

                {/* Name */}
                <div className="mb-4">
                    <label className="block text-sm font-medium text-text-secondary mb-1">
                        {t('library.smartAlbums.name')}
                    </label>
                    <input
                        autoFocus
                        className="w-full bg-bg-primary text-text-primary border border-border rounded-md px-3 py-2 focus:outline-hidden focus:ring-2 focus:ring-accent"
                        onChange={(e) => setName(e.target.value)}
                        placeholder={t('library.smartAlbums.name')}
                        type="text"
                        value={name}
                    />
                </div>

                {/* AI Tags */}
                <div className="mb-4">
                    <label className="block text-sm font-medium text-text-secondary mb-1">
                        {t('library.smartAlbums.criteria.aiTags')}
                    </label>
                    <input
                        className="w-full bg-bg-primary text-text-primary border border-border rounded-md px-3 py-2 focus:outline-hidden focus:ring-2 focus:ring-accent"
                        onChange={(e) => setAiTagsText(e.target.value)}
                        placeholder="landscape, portrait, nature..."
                        type="text"
                        value={aiTagsText}
                    />
                </div>

                {/* User Tags */}
                <div className="mb-4">
                    <label className="block text-sm font-medium text-text-secondary mb-1">
                        {t('library.smartAlbums.criteria.userTags')}
                    </label>
                    <input
                        className="w-full bg-bg-primary text-text-primary border border-border rounded-md px-3 py-2 focus:outline-hidden focus:ring-2 focus:ring-accent"
                        onChange={(e) => setUserTagsText(e.target.value)}
                        placeholder="favorites, vacation..."
                        type="text"
                        value={userTagsText}
                    />
                </div>

                {/* Rating Range */}
                <div className="mb-4">
                    <Text variant={TextVariants.label} className="block mb-2">
                        {t('library.smartAlbums.criteria.ratingRange')}
                    </Text>
                    <div className="space-y-2">
                        <Slider
                            label={t('library.smartAlbums.criteria.ratingRange') + ' (min)'}
                            min={0}
                            max={5}
                            step={1}
                            value={criteria.min_rating ?? 0}
                            onChange={(e) => {
                                const val = Number(typeof e === 'object' && 'target' in e ? e.target.value : e);
                                setCriteria({ ...criteria, min_rating: val > 0 ? val : null });
                            }}
                            defaultValue={0}
                        />
                        <Slider
                            label={t('library.smartAlbums.criteria.ratingRange') + ' (max)'}
                            min={0}
                            max={5}
                            step={1}
                            value={criteria.max_rating ?? 5}
                            onChange={(e) => {
                                const val = Number(typeof e === 'object' && 'target' in e ? e.target.value : e);
                                setCriteria({ ...criteria, max_rating: val < 5 ? val : null });
                            }}
                            defaultValue={5}
                        />
                    </div>
                </div>

                {/* Color Labels */}
                <div className="mb-4">
                    <label className="block text-sm font-medium text-text-secondary mb-2">
                        {t('library.smartAlbums.criteria.colorLabels')}
                    </label>
                    <div className="flex gap-2 flex-wrap">
                        {COLOR_OPTIONS.map((color) => {
                            const colorDef = COLOR_LABELS.find((c) => c.name === color);
                            const isActive = (criteria.color_labels || []).includes(color);
                            return (
                                <button
                                    key={color}
                                    className={`
                                        w-8 h-8 rounded-full border-2 transition-all
                                        ${isActive ? 'border-accent scale-110' : 'border-border-color opacity-50 hover:opacity-80'}
                                    `}
                                    style={{ backgroundColor: colorDef?.color }}
                                    onClick={() => toggleColorLabel(color)}
                                    title={color}
                                />
                            );
                        })}
                    </div>
                </div>

                {/* Date Range */}
                <div className="mb-4">
                    <label className="block text-sm font-medium text-text-secondary mb-1">
                        {t('library.smartAlbums.criteria.dateRange')}
                    </label>
                    <div className="flex gap-2">
                        <input
                            className="flex-1 bg-bg-primary text-text-primary border border-border rounded-md px-3 py-2 focus:outline-hidden focus:ring-2 focus:ring-accent"
                            type="date"
                            value={criteria.date_from || ''}
                            onChange={(e) =>
                                setCriteria({ ...criteria, date_from: e.target.value || null })
                            }
                        />
                        <span className="flex items-center text-text-secondary">—</span>
                        <input
                            className="flex-1 bg-bg-primary text-text-primary border border-border rounded-md px-3 py-2 focus:outline-hidden focus:ring-2 focus:ring-accent"
                            type="date"
                            value={criteria.date_to || ''}
                            onChange={(e) =>
                                setCriteria({ ...criteria, date_to: e.target.value || null })
                            }
                        />
                    </div>
                </div>

                {/* Camera Model */}
                <div className="mb-4">
                    <label className="block text-sm font-medium text-text-secondary mb-1">
                        {t('library.smartAlbums.criteria.camera')}
                    </label>
                    <input
                        className="w-full bg-bg-primary text-text-primary border border-border rounded-md px-3 py-2 focus:outline-hidden focus:ring-2 focus:ring-accent"
                        onChange={(e) => setCameraModelsText(e.target.value)}
                        placeholder="Sony A7III, Canon EOS R5..."
                        type="text"
                        value={cameraModelsText}
                    />
                </div>

                {/* Lens */}
                <div className="mb-4">
                    <label className="block text-sm font-medium text-text-secondary mb-1">
                        {t('library.smartAlbums.criteria.lens')}
                    </label>
                    <input
                        className="w-full bg-bg-primary text-text-primary border border-border rounded-md px-3 py-2 focus:outline-hidden focus:ring-2 focus:ring-accent"
                        onChange={(e) => setLensesText(e.target.value)}
                        placeholder="24-70mm, 85mm f/1.4..."
                        type="text"
                        value={lensesText}
                    />
                </div>

                {/* Toggles */}
                <div className="mb-4 space-y-3">
                    <Switch
                        label={t('library.smartAlbums.criteria.rawOnly')}
                        checked={criteria.raw_only ?? false}
                        onChange={(val) => setCriteria({ ...criteria, raw_only: val || null })}
                    />
                    <Switch
                        label={t('library.smartAlbums.criteria.editedOnly')}
                        checked={criteria.edited_only ?? false}
                        onChange={(val) => setCriteria({ ...criteria, edited_only: val || null })}
                    />
                </div>

                {/* Description Search */}
                <div className="mb-4">
                    <label className="block text-sm font-medium text-text-secondary mb-1">
                        {t('library.smartAlbums.criteria.descriptionSearch')}
                    </label>
                    <input
                        className="w-full bg-bg-primary text-text-primary border border-border rounded-md px-3 py-2 focus:outline-hidden focus:ring-2 focus:ring-accent"
                        onChange={(e) =>
                            setCriteria({ ...criteria, description_search: e.target.value || null })
                        }
                        placeholder="sunset at the beach..."
                        type="text"
                        value={criteria.description_search || ''}
                    />
                </div>

                {/* Buttons */}
                <div className="flex justify-end gap-3 mt-5">
                    <button
                        className="px-4 py-2 rounded-md text-text-secondary hover:bg-surface transition-colors"
                        onClick={onClose}
                    >
                        {t('modals.createFolder.cancel')}
                    </button>
                    <button
                        className="px-4 py-2 rounded-md bg-accent text-button-text font-semibold hover:bg-accent-hover disabled:bg-gray-500 disabled:text-white disabled:cursor-not-allowed transition-colors"
                        disabled={!name.trim()}
                        onClick={handleSave}
                    >
                        {t('modals.createFolder.create')}
                    </button>
                </div>
            </div>
        </div>
    );
}
